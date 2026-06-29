package dev.shallowdusty.oplusotastudio.core.download

import dev.shallowdusty.oplusotastudio.core.model.DownloadState
import dev.shallowdusty.oplusotastudio.core.model.DownloadTaskStore
import dev.shallowdusty.oplusotastudio.core.model.OtaErrorCategory
import dev.shallowdusty.oplusotastudio.core.model.OtaPackage
import dev.shallowdusty.oplusotastudio.core.model.StoredDownloadTask
import java.io.File
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.OkHttpClient
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SimpleDownloadEngineTest {

    private val server = MockWebServer()

    @AfterEach
    fun tearDown() {
        server.close()
    }

    @Test
    fun `downloads package to temp file and verifies checksum`() = runTest {
        server.enqueue(MockResponse(code = 200, body = "abc"))
        server.start()
        val tempRoot = testTempRoot("verified")
        val engine = SimpleDownloadEngine(
            client = OkHttpClient(),
            tempRoot = tempRoot,
            scope = backgroundScope,
        )

        val task = engine.enqueue(
            samplePackage(
                url = server.url("/pkg.zip").toString(),
                md5 = "900150983cd24fb0d6963f7d28e17f72",
            ),
        )

        val finalState = withTimeout(5.seconds) {
            task.state.first { it == DownloadState.Verified }
        }

        assertEquals(DownloadState.Verified, finalState)
        assertEquals("abc", tempRoot.resolve("${task.taskId}.zip.part").readText())
        assertTrue(engine.observeAll().first().contains(task))
    }

    @Test
    fun `checksum mismatch becomes failed state`() = runTest {
        server.enqueue(MockResponse(code = 200, body = "abc"))
        server.start()
        val engine = SimpleDownloadEngine(
            client = OkHttpClient(),
            tempRoot = testTempRoot("mismatch"),
            scope = backgroundScope,
        )

        val task = engine.enqueue(
            samplePackage(
                url = server.url("/pkg.zip").toString(),
                md5 = "00000000000000000000000000000000",
            ),
        )

        val finalState = withTimeout(5.seconds) {
            task.state.first { it is DownloadState.Failed }
        }

        val failed = finalState as DownloadState.Failed
        assertEquals(OtaErrorCategory.ChecksumMismatch, failed.category)
        assertEquals(0, failed.retriesRemaining)
        assertTrue(failed.raw?.contains("expected 00000000000000000000000000000000") == true)
    }

    @Test
    fun `persists queued task and state transitions`() = runTest {
        server.enqueue(MockResponse(code = 200, body = "abc"))
        server.start()
        val store = RecordingDownloadTaskStore()
        val tempRoot = testTempRoot("persisted")
        val engine = SimpleDownloadEngine(
            client = OkHttpClient(),
            tempRoot = tempRoot,
            scope = backgroundScope,
            taskStore = store,
        )

        val task = engine.enqueue(
            samplePackage(
                url = server.url("/pkg.zip").toString(),
                md5 = "900150983cd24fb0d6963f7d28e17f72",
            ),
        )

        withTimeout(5.seconds) {
            task.state.first { it == DownloadState.Verified }
        }

        assertEquals(task.taskId, store.created.single().taskId)
        assertEquals(tempRoot.resolve("${task.taskId}.zip.part").path, store.created.single().tempFilePath)
        assertTrue(store.updates.any { it.state is DownloadState.Running })
        assertTrue(store.updates.any { it.state == DownloadState.Verifying })
        assertEquals(DownloadState.Verified, store.updates.last().state)
    }

    private fun samplePackage(
        url: String,
        md5: String,
    ): OtaPackage =
        OtaPackage(
            versionName = "test",
            type = "full",
            sizeBytes = 3L,
            sourceHost = server.hostName,
            downloadUrl = url,
            md5 = md5,
        )

    private fun testTempRoot(name: String): File {
        val dir = File("build/tmp/simple-download-engine-test/$name")
        dir.deleteRecursively()
        dir.mkdirs()
        return dir
    }

    private data class CreatedTask(
        val taskId: String,
        val tempFilePath: String,
    )

    private data class StateUpdate(
        val taskId: String,
        val state: DownloadState,
    )

    private class RecordingDownloadTaskStore : DownloadTaskStore {
        val created = mutableListOf<CreatedTask>()
        val updates = mutableListOf<StateUpdate>()

        override suspend fun createQueuedTask(
            taskId: String,
            pkg: OtaPackage,
            tempFilePath: String,
            updatedAtMs: Long,
        ) {
            created += CreatedTask(taskId, tempFilePath)
        }

        override suspend fun updateState(
            taskId: String,
            state: DownloadState,
            updatedAtMs: Long,
        ) {
            updates += StateUpdate(taskId, state)
        }

        override suspend fun updateResumeMetadata(
            taskId: String,
            etag: String?,
            lastModified: String?,
            acceptRanges: Boolean,
            updatedAtMs: Long,
        ) = Unit

        override fun observeTasks(): Flow<List<StoredDownloadTask>> = flowOf(emptyList())
    }
}
