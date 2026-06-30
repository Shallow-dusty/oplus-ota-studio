package dev.shallowdusty.oplusotastudio.logging

import dev.shallowdusty.oplusotastudio.core.model.DownloadEngine
import dev.shallowdusty.oplusotastudio.core.model.DownloadState
import dev.shallowdusty.oplusotastudio.core.model.DownloadTask
import dev.shallowdusty.oplusotastudio.core.model.OtaErrorCategory
import dev.shallowdusty.oplusotastudio.core.model.OtaPackage
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test

class LoggingDownloadEngineTest {

    @Test
    fun `logs enqueue and task actions without download URL`() = runTest {
        val sink = RecordingLogSink()
        val delegateTask = RecordingDownloadTask()
        val engine = LoggingDownloadEngine(
            delegate = FixedDownloadEngine(delegateTask),
            logger = AppLogger(sink = sink, minLevel = AppLogLevel.Debug, nowMs = { 20L }),
        )

        val task = engine.enqueue(samplePackage())
        task.pause()
        task.resume()
        task.cancel()

        assertEquals(1, delegateTask.pauseCalls)
        assertEquals(1, delegateTask.resumeCalls)
        assertEquals(1, delegateTask.cancelCalls)
        assertEquals(
            listOf(
                LogWrite(AppLogLevel.Info, "Download", "enqueue requested version=LE2120_14.0.0.1901(CN01) host=otacn.oppo.com sizeBytes=6559817109"),
                LogWrite(AppLogLevel.Info, "Download", "enqueue accepted taskId=task-1"),
                LogWrite(AppLogLevel.Info, "Download", "pause requested taskId=task-1"),
                LogWrite(AppLogLevel.Info, "Download", "resume requested taskId=task-1"),
                LogWrite(AppLogLevel.Info, "Download", "cancel requested taskId=task-1"),
            ),
            sink.writes,
        )
        assertFalse(sink.messages().any { it.contains("signedUrl") || it.contains("pkg.zip") })
    }

    @Test
    fun `logs state transitions without raw failure detail`() = runTest {
        val sink = RecordingLogSink()
        val delegateTask = RecordingDownloadTask(
            stateFlow = flowOf(
                DownloadState.Running(
                    downloadedBytes = 1024L,
                    targetSize = 4096L,
                    speedBytesPerSec = 512L,
                ),
                DownloadState.Failed(
                    category = OtaErrorCategory.Server,
                    retriesRemaining = 2,
                    raw = "HTTP 503 signedUrl=https://example.invalid/token/pkg.zip",
                ),
            ),
        )
        val engine = LoggingDownloadEngine(
            delegate = FixedDownloadEngine(delegateTask),
            logger = AppLogger(sink = sink, minLevel = AppLogLevel.Debug, nowMs = { 21L }),
        )

        engine.enqueue(samplePackage()).state.toList()

        assertEquals(
            listOf(
                LogWrite(AppLogLevel.Info, "Download", "enqueue requested version=LE2120_14.0.0.1901(CN01) host=otacn.oppo.com sizeBytes=6559817109"),
                LogWrite(AppLogLevel.Info, "Download", "enqueue accepted taskId=task-1"),
                LogWrite(AppLogLevel.Info, "Download", "state taskId=task-1 state=Running downloadedBytes=1024 targetSize=4096"),
                LogWrite(AppLogLevel.Error, "Download", "state taskId=task-1 state=Failed category=Server retriesRemaining=2"),
            ),
            sink.writes,
        )
        assertFalse(sink.messages().any { it.contains("HTTP 503") || it.contains("signedUrl") || it.contains("pkg.zip") })
    }

    @Test
    fun `wraps observed tasks`() = runTest {
        val sink = RecordingLogSink()
        val delegateTask = RecordingDownloadTask()
        val engine = LoggingDownloadEngine(
            delegate = FixedDownloadEngine(delegateTask),
            logger = AppLogger(sink = sink, minLevel = AppLogLevel.Info, nowMs = { 22L }),
        )

        val observed = engine.observeAll().first().single()
        observed.pause()

        assertEquals(1, delegateTask.pauseCalls)
        assertEquals(
            listOf(LogWrite(AppLogLevel.Info, "Download", "pause requested taskId=task-1")),
            sink.writes,
        )
    }

    private class FixedDownloadEngine(
        private val task: DownloadTask,
    ) : DownloadEngine {
        override suspend fun enqueue(pkg: OtaPackage): DownloadTask = task

        override fun observeAll(): Flow<List<DownloadTask>> = flowOf(listOf(task))
    }

    private class RecordingDownloadTask(
        private val stateFlow: Flow<DownloadState> = flowOf(DownloadState.Queued),
    ) : DownloadTask {
        override val taskId: String = "task-1"
        override val state: Flow<DownloadState> = stateFlow
        var pauseCalls: Int = 0
            private set
        var resumeCalls: Int = 0
            private set
        var cancelCalls: Int = 0
            private set

        override suspend fun pause() {
            pauseCalls += 1
        }

        override suspend fun resume() {
            resumeCalls += 1
        }

        override suspend fun cancel() {
            cancelCalls += 1
        }
    }

    private class RecordingLogSink : AppLogSink {
        val writes = mutableListOf<LogWrite>()

        override fun append(level: AppLogLevel, tag: String, message: String, nowMs: Long) {
            writes += LogWrite(level, tag, message)
        }

        fun messages(): List<String> = writes.map { it.message }
    }

    private data class LogWrite(
        val level: AppLogLevel,
        val tag: String,
        val message: String,
    )

    private fun samplePackage(): OtaPackage =
        OtaPackage(
            versionName = "LE2120_14.0.0.1901(CN01)",
            type = "full",
            sizeBytes = 6_559_817_109L,
            sourceHost = "otacn.oppo.com",
            downloadUrl = "https://example.invalid/signedUrl/pkg.zip",
            md5 = "5ae1e4d8101218d58c1da10092b22996",
        )
}
