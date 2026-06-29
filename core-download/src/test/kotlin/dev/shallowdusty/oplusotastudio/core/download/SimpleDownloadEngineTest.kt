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
import okhttp3.Headers
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

    @Test
    fun `persists resume metadata from response headers`() = runTest {
        server.enqueue(
            MockResponse(
                code = 200,
                body = "abc",
                headers = Headers.Builder()
                    .add("ETag", "\"abc\"")
                    .add("Last-Modified", "Tue, 30 Jun 2026 00:00:00 GMT")
                    .add("Accept-Ranges", "bytes")
                    .build(),
            ),
        )
        server.start()
        val store = RecordingDownloadTaskStore()
        val engine = SimpleDownloadEngine(
            client = OkHttpClient(),
            tempRoot = testTempRoot("resume-metadata"),
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

        assertEquals(
            ResumeMetadataUpdate(
                taskId = task.taskId,
                etag = "\"abc\"",
                lastModified = "Tue, 30 Jun 2026 00:00:00 GMT",
                acceptRanges = true,
            ),
            store.resumeMetadata.single(),
        )
    }

    @Test
    fun `promotes verified file and records final path`() = runTest {
        server.enqueue(MockResponse(code = 200, body = "abc"))
        server.start()
        val store = RecordingDownloadTaskStore()
        val promoter = RecordingDownloadFilePromoter("content://downloads/pkg.zip")
        val tempRoot = testTempRoot("promoted")
        val engine = SimpleDownloadEngine(
            client = OkHttpClient(),
            tempRoot = tempRoot,
            scope = backgroundScope,
            taskStore = store,
            filePromoter = promoter,
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

        assertEquals(task.taskId, promoter.promotions.single().taskId)
        assertEquals(tempRoot.resolve("${task.taskId}.zip.part"), promoter.promotions.single().sourceFile)
        assertEquals(
            FinalPathUpdate(task.taskId, "content://downloads/pkg.zip"),
            store.finalPaths.single(),
        )
    }

    @Test
    fun `resumes existing partial file with range request`() = runTest {
        server.enqueue(
            MockResponse(
                code = 206,
                body = "c",
                headers = Headers.Builder()
                    .add("ETag", "\"abc\"")
                    .add("Accept-Ranges", "bytes")
                    .build(),
            ),
        )
        server.start()
        val tempRoot = testTempRoot("range-resume")
        val tempFile = tempRoot.resolve("task-1.zip.part")
        tempFile.writeText("ab")
        val pkg = samplePackage(
            url = server.url("/pkg.zip").toString(),
            md5 = "900150983cd24fb0d6963f7d28e17f72",
        )
        val store = RecordingDownloadTaskStore(
            existingTasks = mapOf(
                "task-1" to StoredDownloadTask(
                    taskId = "task-1",
                    pkg = pkg,
                    tempFilePath = tempFile.path,
                    finalFilePath = null,
                    etag = "\"abc\"",
                    lastModified = null,
                    acceptRanges = true,
                    state = DownloadState.Running(2L, 3L, null),
                    updatedAtMs = 100L,
                ),
            ),
        )
        val engine = SimpleDownloadEngine(
            client = OkHttpClient(),
            tempRoot = tempRoot,
            scope = backgroundScope,
            taskStore = store,
            idGenerator = { "task-1" },
        )

        val task = engine.enqueue(pkg)

        withTimeout(5.seconds) {
            task.state.first { it == DownloadState.Verified }
        }

        assertEquals("bytes=2-", server.takeRequest().headers["Range"])
        assertEquals("abc", tempFile.readText())
    }

    @Test
    fun `restarts from zero when server ignores range request`() = runTest {
        server.enqueue(
            MockResponse(
                code = 200,
                body = "abc",
                headers = Headers.Builder()
                    .add("ETag", "\"abc\"")
                    .add("Accept-Ranges", "bytes")
                    .build(),
            ),
        )
        server.start()
        val tempRoot = testTempRoot("range-ignored")
        val tempFile = tempRoot.resolve("task-1.zip.part")
        tempFile.writeText("ab")
        val pkg = samplePackage(
            url = server.url("/pkg.zip").toString(),
            md5 = "900150983cd24fb0d6963f7d28e17f72",
        )
        val store = RecordingDownloadTaskStore(
            existingTasks = mapOf(
                "task-1" to StoredDownloadTask(
                    taskId = "task-1",
                    pkg = pkg,
                    tempFilePath = tempFile.path,
                    finalFilePath = null,
                    etag = "\"abc\"",
                    lastModified = null,
                    acceptRanges = true,
                    state = DownloadState.Running(2L, 3L, null),
                    updatedAtMs = 100L,
                ),
            ),
        )
        val engine = SimpleDownloadEngine(
            client = OkHttpClient(),
            tempRoot = tempRoot,
            scope = backgroundScope,
            taskStore = store,
            idGenerator = { "task-1" },
        )

        val task = engine.enqueue(pkg)

        withTimeout(5.seconds) {
            task.state.first { it == DownloadState.Verified }
        }

        assertEquals("bytes=2-", server.takeRequest().headers["Range"])
        assertEquals("abc", tempFile.readText())
    }

    @Test
    fun `retries from zero when server rejects range request`() = runTest {
        server.enqueue(MockResponse(code = 416))
        server.enqueue(
            MockResponse(
                code = 200,
                body = "abc",
                headers = Headers.Builder()
                    .add("ETag", "\"abc\"")
                    .add("Accept-Ranges", "bytes")
                    .build(),
            ),
        )
        server.start()
        val tempRoot = testTempRoot("range-rejected")
        val tempFile = tempRoot.resolve("task-1.zip.part")
        tempFile.writeText("ab")
        val pkg = samplePackage(
            url = server.url("/pkg.zip").toString(),
            md5 = "900150983cd24fb0d6963f7d28e17f72",
        )
        val store = RecordingDownloadTaskStore(
            existingTasks = mapOf(
                "task-1" to StoredDownloadTask(
                    taskId = "task-1",
                    pkg = pkg,
                    tempFilePath = tempFile.path,
                    finalFilePath = null,
                    etag = "\"abc\"",
                    lastModified = null,
                    acceptRanges = true,
                    state = DownloadState.Running(2L, 3L, null),
                    updatedAtMs = 100L,
                ),
            ),
        )
        val engine = SimpleDownloadEngine(
            client = OkHttpClient(),
            tempRoot = tempRoot,
            scope = backgroundScope,
            taskStore = store,
            idGenerator = { "task-1" },
        )

        val task = engine.enqueue(pkg)

        withTimeout(5.seconds) {
            task.state.first { it == DownloadState.Verified }
        }

        assertEquals("bytes=2-", server.takeRequest().headers["Range"])
        assertEquals(null, server.takeRequest().headers["Range"])
        assertEquals("abc", tempFile.readText())
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

    private data class ResumeMetadataUpdate(
        val taskId: String,
        val etag: String?,
        val lastModified: String?,
        val acceptRanges: Boolean,
    )

    private data class FinalPathUpdate(
        val taskId: String,
        val finalFilePath: String,
    )

    private data class Promotion(
        val taskId: String,
        val sourceFile: File,
    )

    private class RecordingDownloadFilePromoter(
        private val finalPath: String,
    ) : DownloadFilePromoter {
        val promotions = mutableListOf<Promotion>()

        override suspend fun promote(
            taskId: String,
            pkg: OtaPackage,
            sourceFile: File,
        ): PromotedDownloadFile {
            promotions += Promotion(taskId, sourceFile)
            return PromotedDownloadFile(finalPath)
        }
    }

    private class RecordingDownloadTaskStore(
        private val existingTasks: Map<String, StoredDownloadTask> = emptyMap(),
    ) : DownloadTaskStore {
        val created = mutableListOf<CreatedTask>()
        val updates = mutableListOf<StateUpdate>()
        val resumeMetadata = mutableListOf<ResumeMetadataUpdate>()
        val finalPaths = mutableListOf<FinalPathUpdate>()

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
        ) {
            resumeMetadata += ResumeMetadataUpdate(taskId, etag, lastModified, acceptRanges)
        }

        override suspend fun updateFinalFilePath(
            taskId: String,
            finalFilePath: String,
            updatedAtMs: Long,
        ) {
            finalPaths += FinalPathUpdate(taskId, finalFilePath)
        }

        override suspend fun getTask(taskId: String): StoredDownloadTask? = existingTasks[taskId]

        override fun observeTasks(): Flow<List<StoredDownloadTask>> = flowOf(emptyList())
    }
}
