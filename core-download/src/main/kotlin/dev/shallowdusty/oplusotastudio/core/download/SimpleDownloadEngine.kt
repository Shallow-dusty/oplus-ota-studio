package dev.shallowdusty.oplusotastudio.core.download

import dev.shallowdusty.oplusotastudio.core.model.DownloadEngine
import dev.shallowdusty.oplusotastudio.core.model.DownloadState
import dev.shallowdusty.oplusotastudio.core.model.DownloadTask
import dev.shallowdusty.oplusotastudio.core.model.DownloadTaskStore
import dev.shallowdusty.oplusotastudio.core.model.OtaErrorCategory
import dev.shallowdusty.oplusotastudio.core.model.OtaPackage
import dev.shallowdusty.oplusotastudio.core.model.PackageRepository
import dev.shallowdusty.oplusotastudio.core.model.StoredDownloadTask
import dev.shallowdusty.oplusotastudio.core.model.isRetriable
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.RandomAccessFile
import java.util.concurrent.ConcurrentHashMap
import java.util.UUID
import kotlin.coroutines.coroutineContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
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
    private val packageRepository: PackageRepository? = null,
    private val resumeRequestPlanner: ResumeRequestPlanner = ResumeRequestPlanner(),
    private val filePromoter: DownloadFilePromoter? = null,
    private val storagePreflight: DownloadStoragePreflight = DownloadStoragePreflight(),
    private val storageSnapshotProvider: (() -> DownloadStorageSnapshot)? = null,
    private val maxAttempts: Int = 3,
    private val retryDelay: suspend (failedAttempts: Int) -> Unit = { failedAttempts ->
        delay(defaultRetryDelayMillis(failedAttempts))
    },
    private val maxQueuedTasks: Int = DefaultMaxQueuedTasks,
    private val admissionGate: DownloadAdmissionGate = DownloadAdmissionGate.AllowAll,
    private val idGenerator: () -> String = { UUID.randomUUID().toString() },
) : DownloadEngine {

    private val tasks = MutableStateFlow<List<DownloadTask>>(emptyList())
    private val queueMutex = Mutex()
    private val queuedTasks = ArrayDeque<SimpleDownloadTask>()
    private val activeStoredTasks = ConcurrentHashMap<String, SimpleDownloadTask>()
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
        if (storedTask.state.isTerminal || storedTask.state.isUserPaused) return storedTask.state
        pauseStoredTaskForAdmissionGate(taskId)?.let { paused -> return paused }
        val task = SimpleDownloadTask(
            taskId = storedTask.taskId,
            pkg = storedTask.pkg,
            tempFile = File(storedTask.tempFilePath),
            storedTask = storedTask,
        )
        tasks.value = tasks.value.filterNot { it.taskId == taskId } + task
        activeStoredTasks[taskId] = task
        return try {
            task.runToTerminal()
        } finally {
            activeStoredTasks.remove(taskId, task)
        }
    }

    fun stopStoredTask(taskId: String) {
        activeStoredTasks[taskId]?.stopActiveTransfer()
    }

    private suspend fun pauseStoredTaskForAdmissionGate(taskId: String): DownloadState.Paused? {
        if (admissionGate.rejectionReason() == null) return null
        val pauseReason = admissionGate.rejectionPauseReason() ?: return null
        val paused = DownloadState.Paused(pauseReason)
        taskStore?.updateState(
            taskId = taskId,
            state = paused,
            updatedAtMs = nowMs(),
        )
        return paused
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
        @Volatile
        private var stopRequested = false
        @Volatile
        private var stopPersistenceJob: Job? = null
        private var resumeMetadata = storedTask?.let {
            StoredResumeMetadata(
                etag = it.etag,
                lastModified = it.lastModified,
                acceptRanges = it.acceptRanges,
            )
        }

        fun start() {
            pauseRequested = false
            stopRequested = false
            job = scope.launch {
                runToTerminal()
            }
        }

        suspend fun runToTerminal(): DownloadState {
            try {
                runDownload()
            } finally {
                stopPersistenceJob?.join()
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

        fun stopActiveTransfer() {
            val paused = DownloadState.Paused(DownloadState.Paused.PauseReason.NetworkLost)
            stopRequested = true
            _state.value = paused
            stopPersistenceJob = scope.launch { persistState(paused) }
            currentCall?.cancel()
            job?.cancel()
        }

        private suspend fun runDownload() {
            var failedAttempts = 0
            completePartialOutcome()?.let { outcome ->
                when (outcome) {
                    DownloadAttemptOutcome.Finished -> return
                    is DownloadAttemptOutcome.Failed -> {
                        failedAttempts += 1
                        if (!retryOrFinish(outcome, failedAttempts)) return
                    }
                }
            }

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

            while (coroutineContext.isActive && !isUserStopped()) {
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
            completePartialOutcome()?.let { return it }
            val resumePlan = currentResumePlan()
            if (resumePlan?.discardPartial == true) {
                tempFile.delete()
            }
            resumePlan?.truncateToBytes?.let { tempFile.truncateTo(it) }
            var rangeStart = resumePlan?.rangeStart?.takeIf { it > 0L }
            var response = executeRequest(rangeStart)
            if (rangeStart != null && response.code == 416) {
                response.close()
                updateState(
                    DownloadState.Failed(
                        category = OtaErrorCategory.Server,
                        retriesRemaining = maxAttempts - 1,
                        raw = "Server rejected resume range, restarting download from zero",
                    ),
                )
                updateState(
                    DownloadState.Retrying(
                        attempt = 1,
                        maxAttempts = maxAttempts,
                        category = OtaErrorCategory.Server,
                    ),
                )
                tempFile.delete()
                rangeStart = null
                response = executeRequest(rangeStart)
            }
            if (
                rangeStart != null &&
                response.code == 206 &&
                resumeMetadata?.validatorsChanged(response) == true
            ) {
                response.close()
                updateState(
                    DownloadState.Failed(
                        category = OtaErrorCategory.Server,
                        retriesRemaining = maxAttempts - 1,
                        raw = "Server changed package, restarting download from zero",
                    ),
                )
                updateState(
                    DownloadState.Retrying(
                        attempt = 1,
                        maxAttempts = maxAttempts,
                        category = OtaErrorCategory.Server,
                    ),
                )
                tempFile.delete()
                rangeStart = null
                response = executeRequest(rangeStart)
            }
            var targetSize: Long? = null
            var downloaded = 0L
            response.use {
                if (!response.isSuccessful) {
                    return DownloadAttemptOutcome.Failed(
                        category = OtaErrorCategory.Server,
                        raw = "HTTP ${response.code}",
                    )
                }

                val responseResumeMetadata = StoredResumeMetadata(
                    etag = response.header("ETag"),
                    lastModified = response.header("Last-Modified"),
                    acceptRanges = response.header("Accept-Ranges")
                        ?.equals("bytes", ignoreCase = true) == true,
                )
                taskStore?.updateResumeMetadata(
                    taskId = taskId,
                    etag = responseResumeMetadata.etag,
                    lastModified = responseResumeMetadata.lastModified,
                    acceptRanges = responseResumeMetadata.acceptRanges,
                    updatedAtMs = nowMs(),
                )
                resumeMetadata = responseResumeMetadata

                val appendPartial = rangeStart != null && response.code == 206
                targetSize = responseTargetSize(response, rangeStart)
                if (rangeStart != null && !appendPartial) {
                    updateState(
                        DownloadState.Failed(
                            category = OtaErrorCategory.Server,
                            retriesRemaining = maxAttempts - 1,
                            raw = "Server ignored resume range, restarting download from zero",
                        ),
                    )
                    updateState(
                        DownloadState.Retrying(
                            attempt = 1,
                            maxAttempts = maxAttempts,
                            category = OtaErrorCategory.Server,
                        ),
                    )
                    tempFile.delete()
                }
                downloaded = if (appendPartial) rangeStart else 0L
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
                                updateState(
                                    DownloadState.Running(
                                        downloadedBytes = downloaded,
                                        targetSize = targetSize,
                                        speedBytesPerSec = estimateSpeedBytesPerSec(
                                            downloadedBytes = downloaded,
                                            lastProgressUpdateBytes = lastProgressUpdateBytes,
                                            nowMs = nowMs,
                                            lastProgressUpdateAtMs = lastProgressUpdateAtMs,
                                        ),
                                    ),
                                )
                                lastProgressUpdateBytes = downloaded
                                lastProgressUpdateAtMs = nowMs
                            }
                            if (isUserStopped()) return DownloadAttemptOutcome.Finished
                        }
                    }
                }
            }

            if (isUserStopped()) return DownloadAttemptOutcome.Finished
            targetSize?.let { expectedSize ->
                if (downloaded < expectedSize) {
                    return DownloadAttemptOutcome.Failed(
                        category = OtaErrorCategory.Network,
                        raw = "Incomplete download: expected $expectedSize bytes, got $downloaded",
                    )
                }
                if (downloaded > expectedSize) {
                    tempFile.delete()
                    return unexpectedSizeFailure(expectedSize, downloaded)
                }
            }
            return verifyDownloadedFile()
        }

        private suspend fun completePartialOutcome(): DownloadAttemptOutcome? {
            val expectedSize = pkg.sizeBytes.takeIf { it > 0 } ?: return null
            if (!tempFile.exists() || tempFile.length() < expectedSize) return null
            if (tempFile.length() > expectedSize) {
                val actualSize = tempFile.length()
                tempFile.delete()
                return unexpectedSizeFailure(expectedSize, actualSize)
            }
            return verifyDownloadedFile()
        }

        private fun unexpectedSizeFailure(expectedSize: Long, actualSize: Long): DownloadAttemptOutcome.Failed =
            DownloadAttemptOutcome.Failed(
                category = OtaErrorCategory.Network,
                raw = "Unexpected download size: expected $expectedSize bytes, got $actualSize",
            )

        private fun promotionFailure(error: Throwable): DownloadAttemptOutcome.Failed {
            if (error is CancellationException) throw error
            return DownloadAttemptOutcome.Failed(
                category = OtaErrorCategory.File,
                raw = error.message,
            )
        }

        private suspend fun verifyDownloadedFile(): DownloadAttemptOutcome {
            if (isUserStopped()) return DownloadAttemptOutcome.Finished
            updateState(DownloadState.Verifying)
            val result = checksumVerifier.verify(
                file = tempFile,
                expectedSha256 = pkg.sha256,
                expectedMd5 = pkg.md5,
            )
            if (isUserStopped()) return DownloadAttemptOutcome.Finished
            when (result) {
                is ChecksumResult.Verified -> {
                    try {
                        promoteVerifiedFile()
                    } catch (error: Throwable) {
                        return promotionFailure(error)
                    }
                    updateState(DownloadState.Verified)
                }
                ChecksumResult.Unverified -> {
                    try {
                        promoteVerifiedFile()
                    } catch (error: Throwable) {
                        return promotionFailure(error)
                    }
                    updateState(DownloadState.Unverified)
                }
                is ChecksumResult.Mismatch -> {
                    packageRepository?.markChecksumMismatch(
                        packageName = pkg.versionName,
                        sourceHost = pkg.sourceHost,
                        downloadUrl = pkg.downloadUrl,
                        expectedHash = result.expectedHash,
                        actualHash = result.actualHash,
                    )
                    DownloadState.Failed(
                        category = OtaErrorCategory.ChecksumMismatch,
                        retriesRemaining = 0,
                        raw = "expected ${result.expectedHash}, got ${result.actualHash} " +
                            "(${result.algorithm.name}); quarantined at ${quarantineBadFile().path}",
                        expectedHash = result.expectedHash,
                        actualHash = result.actualHash,
                    )
                        .let { updateState(it) }
                }
            }
            return DownloadAttemptOutcome.Finished
        }

        private fun executeRequest(rangeStart: Long?): Response {
            val call = client.newCall(buildRequest(rangeStart))
            currentCall = call
            return call.execute()
        }

        private fun responseTargetSize(response: Response, rangeStart: Long?): Long? {
            pkg.sizeBytes.takeIf { it > 0 }?.let { return it }
            val contentLength = response.header("Content-Length")?.toLongOrNull()
            if (rangeStart != null && response.code == 206) {
                return contentRangeTotalSize(response) ?: contentLength?.let { rangeStart + it }
            }
            return contentLength
        }

        private fun contentRangeTotalSize(response: Response): Long? {
            val total = response.header("Content-Range")
                ?.substringAfter('/', missingDelimiterValue = "")
                ?: return null
            return total
                .takeIf { it.isNotBlank() && it != "*" }
                ?.toLongOrNull()
        }

        private fun isUserStopped(): Boolean =
            stopRequested ||
                pauseRequested ||
                _state.value is DownloadState.Paused ||
                _state.value == DownloadState.Canceled

        private fun shouldEmitProgressUpdate(
            downloadedBytes: Long,
            lastProgressUpdateBytes: Long,
            nowMs: Long,
            lastProgressUpdateAtMs: Long,
        ): Boolean =
            downloadedBytes - lastProgressUpdateBytes >= ProgressUpdateMinBytes ||
                nowMs - lastProgressUpdateAtMs >= ProgressUpdateMinIntervalMs

        private fun estimateSpeedBytesPerSec(
            downloadedBytes: Long,
            lastProgressUpdateBytes: Long,
            nowMs: Long,
            lastProgressUpdateAtMs: Long,
        ): Long {
            val deltaBytes = downloadedBytes - lastProgressUpdateBytes
            if (deltaBytes <= 0L) return 0L
            val elapsedMs = (nowMs - lastProgressUpdateAtMs).coerceAtLeast(1L)
            return (deltaBytes * 1000L / elapsedMs).coerceAtLeast(1L)
        }

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
            retryDelay(failedAttempts)
            return true
        }

        private suspend fun updateState(state: DownloadState) {
            _state.value = state
            persistState(state)
        }

        private suspend fun persistState(state: DownloadState) {
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
            packageRepository?.markDownloaded(
                packageName = pkg.versionName,
                sourceHost = pkg.sourceHost,
                downloadUrl = pkg.downloadUrl,
                downloadedAtMs = nowMs(),
                localFilePath = promoted.finalFilePath,
            )
            tempFile.delete()
        }

        private fun storagePreflightFailure(): DownloadStoragePreflightResult.Failed? {
            val snapshotProvider = storageSnapshotProvider ?: return null
            return storagePreflight.check(
                packageSizeBytes = pkg.sizeBytes,
                snapshot = snapshotProvider(),
                existingTempBytes = preflightRetainedPartialBytes(),
            ) as? DownloadStoragePreflightResult.Failed
        }

        private fun preflightRetainedPartialBytes(): Long {
            val expectedSize = pkg.sizeBytes.takeIf { it > 0 } ?: return 0L
            if (!tempFile.exists() || tempFile.length() <= 0L) return 0L
            val resumePlan = currentResumePlan() ?: return 0L
            if (resumePlan.discardPartial) return 0L
            return resumePlan.rangeStart.coerceIn(0L, expectedSize)
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

        private fun currentResumePlan(): ResumeRequestPlan? {
            val metadata = resumeMetadata ?: return null
            if (!tempFile.exists() || tempFile.length() <= 0L) return null
            val downloadedBytes = when (val current = _state.value) {
                is DownloadState.Running -> current.downloadedBytes
                else -> tempFile.length()
            }
            return resumeRequestPlanner.plan(
                stored = ResumeSnapshot(
                    acceptRanges = metadata.acceptRanges,
                    downloadedBytes = downloadedBytes,
                    partFileBytes = tempFile.length(),
                    etag = metadata.etag,
                    lastModified = metadata.lastModified,
                ),
                current = ResumeValidators(
                    etag = metadata.etag,
                    lastModified = metadata.lastModified,
                ),
            )
        }

        private fun StoredResumeMetadata.validatorsChanged(response: Response): Boolean {
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
        const val DefaultRetryDelayMs = 5_000L
        const val MaxRetryDelayMs = 30_000L

        fun defaultRetryDelayMillis(failedAttempts: Int): Long =
            (DefaultRetryDelayMs * failedAttempts.coerceAtLeast(1))
                .coerceAtMost(MaxRetryDelayMs)
    }
}

private data class StoredResumeMetadata(
    val etag: String?,
    val lastModified: String?,
    val acceptRanges: Boolean,
)

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
            DownloadState.Unverified,
            DownloadState.Canceled -> true
            is DownloadState.Failed -> retriesRemaining <= 0
            else -> false
        }

private val DownloadState.isUserPaused: Boolean
    get() = this == DownloadState.Paused(DownloadState.Paused.PauseReason.User)
