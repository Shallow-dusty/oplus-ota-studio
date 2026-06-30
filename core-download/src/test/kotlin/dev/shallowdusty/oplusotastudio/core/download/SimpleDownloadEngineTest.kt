package dev.shallowdusty.oplusotastudio.core.download

import dev.shallowdusty.oplusotastudio.core.model.DownloadState
import dev.shallowdusty.oplusotastudio.core.model.DownloadTaskStore
import dev.shallowdusty.oplusotastudio.core.model.OtaErrorCategory
import dev.shallowdusty.oplusotastudio.core.model.OtaPackage
import dev.shallowdusty.oplusotastudio.core.model.StoredDownloadTask
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
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
import org.junit.jupiter.api.Assertions.assertFalse
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
        val tempRoot = testTempRoot("mismatch")
        val engine = SimpleDownloadEngine(
            client = OkHttpClient(),
            tempRoot = tempRoot,
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
        assertFalse(tempRoot.resolve("${task.taskId}.zip.part").exists())
        assertEquals("abc", tempRoot.resolve("${task.taskId}.zip.bad").readText())
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
        assertFalse(tempRoot.resolve("${task.taskId}.zip.part").exists())
        assertEquals(
            FinalPathUpdate(task.taskId, "content://downloads/pkg.zip"),
            store.finalPaths.single(),
        )
    }

    @Test
    fun `promotion io failure becomes file failure without retrying download`() = runTest {
        server.enqueue(MockResponse(code = 200, body = "abc"))
        server.start()
        val store = RecordingDownloadTaskStore()
        val engine = SimpleDownloadEngine(
            client = OkHttpClient(),
            tempRoot = testTempRoot("promotion-failure"),
            scope = backgroundScope,
            taskStore = store,
            filePromoter = ThrowingDownloadFilePromoter(IOException("Downloads write failed")),
            maxAttempts = 1,
        )

        val task = engine.enqueue(
            samplePackage(
                url = server.url("/pkg.zip").toString(),
                md5 = "900150983cd24fb0d6963f7d28e17f72",
            ),
        )

        val finalState = withTimeout(5.seconds) {
            task.state.first { it is DownloadState.Failed }
        }

        val failed = finalState as DownloadState.Failed
        assertEquals(OtaErrorCategory.File, failed.category)
        assertEquals(0, failed.retriesRemaining)
        assertEquals("Downloads write failed", failed.raw)
        assertEquals(1, server.requestCount)
        assertEquals(failed, store.updates.last().state)
    }

    @Test
    fun `fails before network request when storage preflight fails`() = runTest {
        val store = RecordingDownloadTaskStore()
        val engine = SimpleDownloadEngine(
            client = OkHttpClient(),
            tempRoot = testTempRoot("storage-preflight"),
            scope = backgroundScope,
            taskStore = store,
            storageSnapshotProvider = {
                DownloadStorageSnapshot(
                    tempAvailableBytes = 1L,
                    finalAvailableBytes = 1L,
                    tempAndFinalShareVolume = true,
                )
            },
        )

        val task = engine.enqueue(
            OtaPackage(
                versionName = "test",
                type = "full",
                sizeBytes = 3L,
                sourceHost = "127.0.0.1",
                downloadUrl = "http://127.0.0.1:1/pkg.zip",
                md5 = "900150983cd24fb0d6963f7d28e17f72",
            ),
        )

        val finalState = withTimeout(5.seconds) {
            task.state.first { it is DownloadState.Failed }
        }

        val failed = finalState as DownloadState.Failed
        assertEquals(OtaErrorCategory.File, failed.category)
        assertEquals(0, failed.retriesRemaining)
        assertTrue(failed.raw?.contains("Download requires") == true)
        assertEquals(failed, store.updates.last().state)
    }

    @Test
    fun `retries server error before verified download`() = runTest {
        server.enqueue(MockResponse(code = 503, body = "try later"))
        server.enqueue(MockResponse(code = 200, body = "abc"))
        server.start()
        val store = RecordingDownloadTaskStore()
        val engine = SimpleDownloadEngine(
            client = OkHttpClient(),
            tempRoot = testTempRoot("server-retry"),
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

        assertEquals(2, server.requestCount)
        assertTrue(
            store.updates.any {
                it.state == DownloadState.Retrying(
                    attempt = 1,
                    maxAttempts = 3,
                    category = OtaErrorCategory.Server,
                )
            },
        )
        assertEquals(DownloadState.Verified, store.updates.last().state)
    }

    @Test
    fun `executes persisted queued task by id`() = runTest {
        server.enqueue(MockResponse(code = 200, body = "abc"))
        server.start()
        val tempRoot = testTempRoot("execute-stored-task")
        val tempFile = tempRoot.resolve("task-1.zip.part")
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
                    etag = null,
                    lastModified = null,
                    acceptRanges = false,
                    state = DownloadState.Queued,
                    updatedAtMs = 100L,
                ),
            ),
        )
        val engine = SimpleDownloadEngine(
            client = OkHttpClient(),
            tempRoot = tempRoot,
            scope = backgroundScope,
            taskStore = store,
        )

        val finalState = engine.executeStoredTask("task-1")

        assertEquals(DownloadState.Verified, finalState)
        assertTrue(store.created.isEmpty())
        assertEquals("abc", tempFile.readText())
        assertEquals(DownloadState.Verified, store.updates.last().state)
    }

    @Test
    fun `execute stored task returns verified terminal state without redownloading`() = runTest {
        server.start()
        val tempRoot = testTempRoot("execute-stored-verified-task")
        val pkg = samplePackage(
            url = server.url("/pkg.zip").toString(),
            md5 = "900150983cd24fb0d6963f7d28e17f72",
        )
        val store = RecordingDownloadTaskStore(
            existingTasks = mapOf(
                "task-1" to StoredDownloadTask(
                    taskId = "task-1",
                    pkg = pkg,
                    tempFilePath = tempRoot.resolve("task-1.zip.part").path,
                    finalFilePath = "content://downloads/pkg.zip",
                    etag = null,
                    lastModified = null,
                    acceptRanges = false,
                    state = DownloadState.Verified,
                    updatedAtMs = 100L,
                ),
            ),
        )
        val engine = SimpleDownloadEngine(
            client = OkHttpClient(),
            tempRoot = tempRoot,
            scope = backgroundScope,
            taskStore = store,
        )

        val finalState = engine.executeStoredTask("task-1")

        assertEquals(DownloadState.Verified, finalState)
        assertEquals(0, server.requestCount)
        assertTrue(store.updates.isEmpty())
    }

    @Test
    fun `keeps later downloads queued until active download finishes`() = runTest {
        val engineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        try {
            server.enqueue(
                MockResponse.Builder()
                    .code(200)
                    .body("abc")
                    .bodyDelay(750, TimeUnit.MILLISECONDS)
                    .build(),
            )
            server.enqueue(MockResponse(code = 200, body = "abc"))
            server.start()
            val engine = SimpleDownloadEngine(
                client = OkHttpClient(),
                tempRoot = testTempRoot("serial-queue"),
                scope = engineScope,
            )

            engine.enqueue(
                samplePackage(
                    url = server.url("/first.zip").toString(),
                    md5 = "900150983cd24fb0d6963f7d28e17f72",
                ),
            )
            val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5)
            while (server.requestCount < 1 && System.nanoTime() < deadline) {
                Thread.sleep(10)
            }
            assertEquals(1, server.requestCount)

            val second = engine.enqueue(
                samplePackage(
                    url = server.url("/second.zip").toString(),
                    md5 = "900150983cd24fb0d6963f7d28e17f72",
                ),
            )
            Thread.sleep(100)

            assertEquals(DownloadState.Queued, second.state.first())
            assertEquals(1, server.requestCount)
        } finally {
            engineScope.cancel()
        }
    }

    @Test
    fun `cancel deletes part file and removes stored task`() = runTest {
        val store = RecordingDownloadTaskStore()
        val tempRoot = testTempRoot("cancel-cleanup")
        val tempFile = tempRoot.resolve("task-1.zip.part").also { it.writeText("partial") }
        val engine = SimpleDownloadEngine(
            client = OkHttpClient(),
            tempRoot = tempRoot,
            scope = backgroundScope,
            taskStore = store,
            idGenerator = { "task-1" },
        )
        val task = engine.enqueue(
            OtaPackage(
                versionName = "test",
                type = "full",
                sizeBytes = 3L,
                sourceHost = "127.0.0.1",
                downloadUrl = "http://127.0.0.1:1/pkg.zip",
                md5 = null,
            ),
        )

        task.cancel()

        assertFalse(tempFile.exists())
        assertEquals(StateUpdate(task.taskId, DownloadState.Canceled), store.updates.single())
        assertEquals(listOf(task.taskId), store.deleted)
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

    @Test
    fun `restarts from zero when resumed response validators changed`() = runTest {
        server.enqueue(
            MockResponse(
                code = 206,
                body = "c",
                headers = Headers.Builder()
                    .add("ETag", "\"new\"")
                    .add("Accept-Ranges", "bytes")
                    .build(),
            ),
        )
        server.enqueue(
            MockResponse(
                code = 200,
                body = "abc",
                headers = Headers.Builder()
                    .add("ETag", "\"new\"")
                    .add("Accept-Ranges", "bytes")
                    .build(),
            ),
        )
        server.start()
        val tempRoot = testTempRoot("range-validator-changed")
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
                    etag = "\"old\"",
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

    private class ThrowingDownloadFilePromoter(
        private val error: IOException,
    ) : DownloadFilePromoter {
        override suspend fun promote(
            taskId: String,
            pkg: OtaPackage,
            sourceFile: File,
        ): PromotedDownloadFile {
            throw error
        }
    }

    private class RecordingDownloadTaskStore(
        private val existingTasks: Map<String, StoredDownloadTask> = emptyMap(),
    ) : DownloadTaskStore {
        val created = mutableListOf<CreatedTask>()
        val updates = mutableListOf<StateUpdate>()
        val resumeMetadata = mutableListOf<ResumeMetadataUpdate>()
        val finalPaths = mutableListOf<FinalPathUpdate>()
        val deleted = mutableListOf<String>()

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

        override suspend fun deleteTask(taskId: String) {
            deleted += taskId
        }

        override fun observeTasks(): Flow<List<StoredDownloadTask>> = flowOf(emptyList())
    }
}
