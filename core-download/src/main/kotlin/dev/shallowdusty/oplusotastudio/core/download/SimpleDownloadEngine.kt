package dev.shallowdusty.oplusotastudio.core.download

import dev.shallowdusty.oplusotastudio.core.model.DownloadEngine
import dev.shallowdusty.oplusotastudio.core.model.DownloadState
import dev.shallowdusty.oplusotastudio.core.model.DownloadTask
import dev.shallowdusty.oplusotastudio.core.model.DownloadTaskStore
import dev.shallowdusty.oplusotastudio.core.model.OtaErrorCategory
import dev.shallowdusty.oplusotastudio.core.model.OtaPackage
import java.io.File
import java.io.IOException
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
import okhttp3.OkHttpClient
import okhttp3.Request

class SimpleDownloadEngine(
    private val client: OkHttpClient = OkHttpClient(),
    private val tempRoot: File,
    private val checksumVerifier: ChecksumVerifier = ChecksumVerifier(),
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
    private val taskStore: DownloadTaskStore? = null,
) : DownloadEngine {

    private val tasks = MutableStateFlow<List<DownloadTask>>(emptyList())

    override suspend fun enqueue(pkg: OtaPackage): DownloadTask {
        tempRoot.mkdirs()
        val taskId = UUID.randomUUID().toString()
        val tempFile = tempRoot.resolve("$taskId.zip.part")
        taskStore?.createQueuedTask(
            taskId = taskId,
            pkg = pkg,
            tempFilePath = tempFile.path,
            updatedAtMs = nowMs(),
        )
        val task = SimpleDownloadTask(
            taskId = taskId,
            pkg = pkg,
            tempFile = tempFile,
        )
        tasks.value = tasks.value + task
        task.start()
        return task
    }

    override fun observeAll(): Flow<List<DownloadTask>> = tasks.asStateFlow()

    private inner class SimpleDownloadTask(
        override val taskId: String,
        val pkg: OtaPackage,
        val tempFile: File,
    ) : DownloadTask {
        private val _state = MutableStateFlow<DownloadState>(DownloadState.Queued)
        override val state: Flow<DownloadState> = _state.asStateFlow()
        private var job: Job? = null

        fun start() {
            job = scope.launch {
                runDownload()
            }
        }

        override suspend fun pause() {
            updateState(DownloadState.Paused(DownloadState.Paused.PauseReason.User))
        }

        override suspend fun resume() {
            if (_state.value is DownloadState.Paused) {
                updateState(DownloadState.Running(tempFile.length(), pkg.sizeBytes, null))
            }
        }

        override suspend fun cancel() {
            job?.cancel()
            updateState(DownloadState.Canceled)
        }

        private suspend fun runDownload() {
            try {
                val request = Request.Builder()
                    .url(pkg.downloadUrl)
                    .get()
                    .build()
                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        updateState(
                            DownloadState.Failed(
                                category = OtaErrorCategory.Server,
                                retriesRemaining = 0,
                                raw = "HTTP ${response.code}",
                            ),
                        )
                        return
                    }

                    val targetSize = pkg.sizeBytes.takeIf { it > 0 }
                        ?: response.header("Content-Length")?.toLongOrNull()
                    var downloaded = 0L
                    updateState(DownloadState.Running(downloaded, targetSize, null))

                    response.body.byteStream().use { input ->
                        tempFile.outputStream().use { output ->
                            val buffer = ByteArray(1024 * 1024)
                            while (coroutineContext.isActive) {
                                val read = input.read(buffer)
                                if (read == -1) break
                                output.write(buffer, 0, read)
                                downloaded += read
                                updateState(DownloadState.Running(downloaded, targetSize, null))
                            }
                        }
                    }
                }

                updateState(DownloadState.Verifying)
                updateState(when (
                    val result = checksumVerifier.verify(
                        file = tempFile,
                        expectedSha256 = pkg.sha256,
                        expectedMd5 = pkg.md5,
                    )
                ) {
                    is ChecksumResult.Verified,
                    ChecksumResult.Unverified -> DownloadState.Verified
                    is ChecksumResult.Mismatch -> DownloadState.Failed(
                        category = OtaErrorCategory.ChecksumMismatch,
                        retriesRemaining = 0,
                        raw = "expected ${result.expectedHash}, got ${result.actualHash} (${result.algorithm.name})",
                    )
                })
            } catch (error: IOException) {
                updateState(
                    DownloadState.Failed(
                        category = OtaErrorCategory.Network,
                        retriesRemaining = 0,
                        raw = error.message,
                    ),
                )
            }
        }

        private suspend fun updateState(state: DownloadState) {
            _state.value = state
            taskStore?.updateState(
                taskId = taskId,
                state = state,
                updatedAtMs = nowMs(),
            )
        }
    }

    private fun nowMs(): Long = System.currentTimeMillis()
}
