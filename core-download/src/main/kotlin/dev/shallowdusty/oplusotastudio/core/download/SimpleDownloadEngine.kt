package dev.shallowdusty.oplusotastudio.core.download

import dev.shallowdusty.oplusotastudio.core.model.DownloadEngine
import dev.shallowdusty.oplusotastudio.core.model.DownloadState
import dev.shallowdusty.oplusotastudio.core.model.DownloadTask
import dev.shallowdusty.oplusotastudio.core.model.DownloadTaskStore
import dev.shallowdusty.oplusotastudio.core.model.OtaErrorCategory
import dev.shallowdusty.oplusotastudio.core.model.OtaPackage
import dev.shallowdusty.oplusotastudio.core.model.StoredDownloadTask
import dev.shallowdusty.oplusotastudio.core.model.isRetriable
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.RandomAccessFile
import java.util.UUID
import kotlin.coroutines.coroutineContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okhttp3.Call
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response

class SimpleDownloadEngine(
    private val client: OkHttpClient = OkHttpClient(),
    private val tempRoot: File,
    private val checksumVerifier: ChecksumVerifier = ChecksumVerifier(),
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
    private val taskStore: DownloadTaskStore? = null,
    private val resumeRequestPlanner: ResumeRequestPlanner = ResumeRequestPlanner(),
    private val filePromoter: DownloadFilePromoter? = null,
    private val storagePreflight: DownloadStoragePreflight = DownloadStoragePreflight(),
    private val storageSnapshotProvider: (() -> DownloadStorageSnapshot)? = null,
    private val maxAttempts: Int = 3,
    private val maxQueuedTasks: Int = DefaultMaxQueuedTasks,
    private val admissionGate: DownloadAdmissionGate = DownloadAdmissionGate.AllowAll,
    private val idGenerator: () -> String = { UUID.randomUUID().toString() },
) : DownloadEngine {

    private val tasks = MutableStateFlow<List<DownloadTask>>(emptyList())
    private val queueMutex = Mutex()
    private val queuedTasks = ArrayDeque<SimpleDownloadTask>()
    private var activeTask: SimpleDownloadTask? = null

    override suspend fun enqueue(pkg: OtaPackage): DownloadTask {
        tempRoot.mkdirs()
        return queueMutex.withLock {
            val taskId = idGenerator()
            admissionGate.rejectionReason()?.let { reason ->
                return@withLock RejectedDownloadTask(
                    taskId = taskId,
                    raw = reason,
                ).also { task ->
                    tasks.value = tasks.value + task
                }
            }
            if (activeQueueSize() >= maxQueuedTasks) {
                return@withLock RejectedDownloadTask(
                    taskId = taskId,
                    raw = "Download queue limit reached ($maxQueuedTasks tasks)",
                ).also { task ->
                    tasks.value = tasks.value + task
                }
            }
            val storedTask = taskStore?.getTask(taskId)
            val tempFile = storedTask?.tempFilePath?.let(::File) ?: tempRoot.resolve("$taskId.zip.part")
            if (storedTask == null) {
                taskStore?.createQueuedTask(
                    taskId = taskId,
                    pkg = pkg,
                    tempFilePath = tempFile.path,
                    updatedAtMs = nowMs(),
                )
            }
            val task = SimpleDownloadTask(
                taskId = taskId,
                pkg = pkg,
                tempFile = tempFile,
                storedTask = storedTask,
            )
            tasks.value = tasks.value + task
            queuedTasks.addLast(task)
            startNextTaskIfIdle()
            task
        }
    }

    override fun observeAll(): Flow<List<DownloadTask>> = tasks.asStateFlow()

    suspend fun executeStoredTask(taskId: String): DownloadState {
        val storedTask = taskStore?.getTask(taskId)
            ?: return DownloadState.Failed(
                category = OtaErrorCategory.File,
                retriesRemaining = 0,
                raw = "Download task not found: $taskId",
            )
        if (storedTask.state.isTerminal) return storedTask.state
        val task = SimpleDownloadTask(
            taskId = storedTask.taskId,
            pkg = storedTask.pkg,
            tempFile = File(storedTask.tempFilePath),
            storedTask = storedTask,
        )
        tasks.value = tasks.value + task
        return task.runToTerminal()
    }

    private inner class SimpleDownloadTask(
        override val taskId: String,
        val pkg: OtaPackage,
        val tempFile: File,
        val storedTask: StoredDownloadTask?,
    ) : DownloadTask {
        private val _state = MutableStateFlow<DownloadState>(DownloadState.Queued)
        override val state: Flow<DownloadState> = _state.asStateFlow()
        @Volatile
        private var job: Job? = null
        @Volatile
        private var currentCall: Call? = null
        @Volatile
        private var pauseRequested = false

        fun start() {
            pauseRequested = false
            job = scope.launch {
                runToTerminal()
            }
        }

        suspend fun runToTerminal(): DownloadState {
            try {
                runDownload()
            } finally {
                finishTask(this@SimpleDownloadTask)
            }
            return _state.value
        }

        override suspend fun pause() {
            if (_state.value !is DownloadState.Running) return
            pauseRequested = true
            updateState(DownloadState.Paused(DownloadState.Paused.PauseReason.User))
            currentCall?.cancel()
            job?.cancel()
        }

        override suspend fun resume() {
            if (_state.value is DownloadState.Paused) {
                updateState(DownloadState.Queued)
                queueMutex.withLock {
                    queuedTasks.addLast(this)
                    startNextTaskIfIdle()
                }
            }
        }

        override suspend fun cancel() {
            removeQueuedTask(this)
            currentCall?.cancel()
            job?.cancel()
            updateState(DownloadState.Canceled)
            tempFile.delete()
            taskStore?.deleteTask(taskId)
        }

        private suspend fun runDownload() {
            storagePreflightFailure()?.let { failure ->
                updateState(
                    DownloadState.Failed(
                        category = OtaErrorCategory.File,
                        retriesRemaining = 0,
                        raw = failure.reason,
                    ),
                )
                return
            }

            var failedAttempts = 0
            while (coroutineContext.isActive) {
                val outcome = try {
                    runDownloadAttempt()
                } catch (error: IOException) {
                    if (isUserStopped()) {
                        return
                    }
                    DownloadAttemptOutcome.Failed(
                        category = OtaErrorCategory.Network,
                        raw = error.message,
                    )
                }

                when (outcome) {
                    DownloadAttemptOutcome.Finished -> return
                    is DownloadAttemptOutcome.Failed -> {
                        failedAttempts += 1
                        if (!retryOrFinish(outcome, failedAttempts)) return
                    }
                }
            }
        }

        private suspend fun runDownloadAttempt(): DownloadAttemptOutcome {
            val resumePlan = storedTask?.resumePlan(tempFile)
            if (resumePlan?.discardPartial == true) {
                tempFile.delete()
            }
            resumePlan?.truncateToBytes?.let { tempFile.truncateTo(it) }
            var rangeStart = resumePlan?.rangeStart?.takeIf { it > 0L }
            var response = executeRequest(rangeStart)
            if (rangeStart != null && response.code == 416) {
                response.close()
                tempFile.delete()
                rangeStart = null
                response = executeRequest(rangeStart)
            }
            if (
                rangeStart != null &&
                response.code == 206 &&
                storedTask?.validatorsChanged(response) == true
            ) {
                response.close()
                tempFile.delete()
                rangeStart = null
                response = executeRequest(rangeStart)
            }
            response.use {
                if (!response.isSuccessful) {
                    return DownloadAttemptOutcome.Failed(
                        category = OtaErrorCategory.Server,
                        raw = "HTTP ${response.code}",
                    )
                }

                taskStore?.updateResumeMetadata(
                    taskId = taskId,
                    etag = response.header("ETag"),
                    lastModified = response.header("Last-Modified"),
                    acceptRanges = response.header("Accept-Ranges")
                        ?.equals("bytes", ignoreCase = true) == true,
                    updatedAtMs = nowMs(),
                )

                val targetSize = pkg.sizeBytes.takeIf { it > 0 }
                    ?: response.header("Content-Length")?.toLongOrNull()
                val appendPartial = rangeStart != null && response.code == 206
                if (rangeStart != null && !appendPartial) {
                    tempFile.delete()
                }
                var downloaded = if (appendPartial) rangeStart else 0L
                updateState(DownloadState.Running(downloaded, targetSize, null))
                var lastProgressUpdateBytes = downloaded
                var lastProgressUpdateAtMs = nowMs()

                response.body.byteStream().use { input ->
                    FileOutputStream(tempFile, appendPartial).use { output ->
                        val buffer = ByteArray(1024 * 1024)
                        while (coroutineContext.isActive) {
                            val read = input.read(buffer)
                            if (read == -1) break
                            output.write(buffer, 0, read)
                            downloaded += read
                            val nowMs = nowMs()
                            if (
                                shouldEmitProgressUpdate(
                                    downloadedBytes = downloaded,
                                    lastProgressUpdateBytes = lastProgressUpdateBytes,
                                    nowMs = nowMs,
                                    lastProgressUpdateAtMs = lastProgressUpdateAtMs,
                                )
                            ) {
                                updateState(DownloadState.Running(downloaded, targetSize, null))
                                lastProgressUpdateBytes = downloaded
                                lastProgressUpdateAtMs = nowMs
                            }
                            if (isUserStopped()) return DownloadAttemptOutcome.Finished
                        }
                    }
                }
            }

            if (isUserStopped()) return DownloadAttemptOutcome.Finished
            updateState(DownloadState.Verifying)
            when (
                val result = checksumVerifier.verify(
                    file = tempFile,
                    expectedSha256 = pkg.sha256,
                    expectedMd5 = pkg.md5,
                )
            ) {
                is ChecksumResult.Verified,
                ChecksumResult.Unverified -> {
                    try {
                        promoteVerifiedFile()
                    } catch (error: IOException) {
                        return DownloadAttemptOutcome.Failed(
                            category = OtaErrorCategory.File,
                            raw = error.message,
                        )
                    }
                    updateState(DownloadState.Verified)
                }
                is ChecksumResult.Mismatch -> DownloadState.Failed(
                    category = OtaErrorCategory.ChecksumMismatch,
                    retriesRemaining = 0,
                    raw = "expected ${result.expectedHash}, got ${result.actualHash} " +
                        "(${result.algorithm.name}); quarantined at ${quarantineBadFile().path}",
                )
                    .let { updateState(it) }
            }
            return DownloadAttemptOutcome.Finished
        }

        private fun executeRequest(rangeStart: Long?): Response {
            val call = client.newCall(buildRequest(rangeStart))
            currentCall = call
            return call.execute()
        }

        private fun isUserStopped(): Boolean =
            pauseRequested || _state.value is DownloadState.Paused || _state.value == DownloadState.Canceled

        private fun shouldEmitProgressUpdate(
            downloadedBytes: Long,
            lastProgressUpdateBytes: Long,
            nowMs: Long,
            lastProgressUpdateAtMs: Long,
        ): Boolean =
            downloadedBytes - lastProgressUpdateBytes >= ProgressUpdateMinBytes ||
                nowMs - lastProgressUpdateAtMs >= ProgressUpdateMinIntervalMs

        private suspend fun retryOrFinish(
            outcome: DownloadAttemptOutcome.Failed,
            failedAttempts: Int,
        ): Boolean {
            val retriesRemaining = (maxAttempts - failedAttempts).coerceAtLeast(0)
            updateState(
                DownloadState.Failed(
                    category = outcome.category,
                    retriesRemaining = retriesRemaining,
                    raw = outcome.raw,
                ),
            )
            if (!outcome.category.isRetriable || retriesRemaining <= 0) {
                return false
            }
            updateState(
                DownloadState.Retrying(
                    attempt = failedAttempts,
                    maxAttempts = maxAttempts,
                    category = outcome.category,
                ),
            )
            return true
        }

        private suspend fun updateState(state: DownloadState) {
            _state.value = state
            taskStore?.updateState(
                taskId = taskId,
                state = state,
                updatedAtMs = nowMs(),
            )
        }

        private suspend fun promoteVerifiedFile() {
            val promoted = filePromoter?.promote(
                taskId = taskId,
                pkg = pkg,
                sourceFile = tempFile,
            ) ?: return
            taskStore?.updateFinalFilePath(
                taskId = taskId,
                finalFilePath = promoted.finalFilePath,
                updatedAtMs = nowMs(),
            )
            tempFile.delete()
        }

        private fun storagePreflightFailure(): DownloadStoragePreflightResult.Failed? {
            val snapshotProvider = storageSnapshotProvider ?: return null
            return storagePreflight.check(
                packageSizeBytes = pkg.sizeBytes,
                snapshot = snapshotProvider(),
            ) as? DownloadStoragePreflightResult.Failed
        }

        private fun quarantineBadFile(): File {
            val badFile = tempFile.resolveSibling("$taskId.zip.bad")
            badFile.delete()
            if (tempFile.exists()) {
                tempFile.renameTo(badFile)
            }
            return badFile
        }

        private fun buildRequest(rangeStart: Long?): Request {
            val requestBuilder = Request.Builder()
                .url(pkg.downloadUrl)
                .get()
            rangeStart?.let { requestBuilder.header("Range", "bytes=$it-") }
            return requestBuilder.build()
        }

        private fun StoredDownloadTask.resumePlan(tempFile: File): ResumeRequestPlan? {
            if (!tempFile.exists() || tempFile.length() <= 0L) return null
            val downloadedBytes = when (val current = state) {
                is DownloadState.Running -> current.downloadedBytes
                else -> tempFile.length()
            }
            return resumeRequestPlanner.plan(
                stored = ResumeSnapshot(
                    acceptRanges = acceptRanges,
                    downloadedBytes = downloadedBytes,
                    partFileBytes = tempFile.length(),
                    etag = etag,
                    lastModified = lastModified,
                ),
                current = ResumeValidators(
                    etag = etag,
                    lastModified = lastModified,
                ),
            )
        }

        private fun StoredDownloadTask.validatorsChanged(response: Response): Boolean {
            val responseEtag = response.header("ETag")
            val responseLastModified = response.header("Last-Modified")
            val etagChanged = etag != null &&
                responseEtag != null &&
                etag != responseEtag
            val lastModifiedChanged = lastModified != null &&
                responseLastModified != null &&
                lastModified != responseLastModified
            return etagChanged || lastModifiedChanged
        }
    }

    private fun startNextTaskIfIdle() {
        if (activeTask != null) return
        val next = queuedTasks.removeFirstOrNull() ?: return
        activeTask = next
        next.start()
    }

    private suspend fun finishTask(task: SimpleDownloadTask) {
        queueMutex.withLock {
            if (activeTask === task) {
                activeTask = null
                startNextTaskIfIdle()
            }
        }
    }

    private suspend fun removeQueuedTask(task: SimpleDownloadTask) {
        queueMutex.withLock {
            queuedTasks.remove(task)
        }
    }

    private fun activeQueueSize(): Int =
        queuedTasks.size + if (activeTask != null) 1 else 0

    private fun nowMs(): Long = System.currentTimeMillis()

    private fun File.truncateTo(bytes: Long) {
        RandomAccessFile(this, "rw").use { it.setLength(bytes) }
    }

    private companion object {
        const val DefaultMaxQueuedTasks = 20
        const val ProgressUpdateMinBytes = 1024L * 1024L
        const val ProgressUpdateMinIntervalMs = 250L
    }
}

private class RejectedDownloadTask(
    override val taskId: String,
    raw: String,
) : DownloadTask {
    private val currentState = MutableStateFlow(
        DownloadState.Failed(
            category = OtaErrorCategory.File,
            retriesRemaining = 0,
            raw = raw,
        ),
    )
    override val state: Flow<DownloadState> = currentState.asStateFlow()

    override suspend fun pause() = Unit

    override suspend fun resume() = Unit

    override suspend fun cancel() = Unit
}

private sealed interface DownloadAttemptOutcome {
    data object Finished : DownloadAttemptOutcome

    data class Failed(
        val category: OtaErrorCategory,
        val raw: String?,
    ) : DownloadAttemptOutcome
}

private val DownloadState.isTerminal: Boolean
    get() =
        when (this) {
            DownloadState.Verified,
            DownloadState.Canceled -> true
            is DownloadState.Failed -> retriesRemaining <= 0
            else -> false
        }
