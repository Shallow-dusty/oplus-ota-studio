package dev.shallowdusty.oplusotastudio.core.download

import dev.shallowdusty.oplusotastudio.core.model.DownloadState
import dev.shallowdusty.oplusotastudio.core.model.DownloadTask
import dev.shallowdusty.oplusotastudio.core.model.DownloadTaskStore
import dev.shallowdusty.oplusotastudio.core.model.HistoryEntry
import dev.shallowdusty.oplusotastudio.core.model.OtaErrorCategory
import dev.shallowdusty.oplusotastudio.core.model.OtaPackage
import dev.shallowdusty.oplusotastudio.core.model.PackageRepository
import dev.shallowdusty.oplusotastudio.core.model.StoredDownloadTask
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
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
import okhttp3.MediaType
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import okio.BufferedSource
import okio.Source
import okio.Timeout
import okio.buffer
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
    fun `download without checksum becomes unverified`() = runTest {
        server.enqueue(MockResponse(code = 200, body = "abc"))
        server.start()
        val store = RecordingDownloadTaskStore()
        val engine = SimpleDownloadEngine(
            client = OkHttpClient(),
            tempRoot = testTempRoot("unverified"),
            scope = backgroundScope,
            taskStore = store,
        )

        val task = engine.enqueue(
            samplePackage(
                url = server.url("/pkg.zip").toString(),
                md5 = null,
            ),
        )

        val finalState = withTimeout(5.seconds) {
            task.state.first { it == DownloadState.Unverified }
        }

        assertEquals(DownloadState.Unverified, finalState)
        assertEquals(DownloadState.Unverified, store.updates.last().state)
    }

    @Test
    fun `short download without checksum fails when package size is known`() = runTest {
        server.enqueue(MockResponse(code = 200, body = "ab"))
        server.start()
        val tempRoot = testTempRoot("short-unverified")
        val store = RecordingDownloadTaskStore()
        val promoter = RecordingDownloadFilePromoter(
            finalPath = tempRoot.resolve("final.zip").path,
        )
        val engine = SimpleDownloadEngine(
            client = OkHttpClient(),
            tempRoot = tempRoot,
            scope = backgroundScope,
            taskStore = store,
            filePromoter = promoter,
            maxAttempts = 1,
        )

        val task = engine.enqueue(
            samplePackage(
                url = server.url("/pkg.zip").toString(),
                md5 = null,
            ),
        )

        val finalState = withTimeout(5.seconds) {
            task.state.first { it is DownloadState.Failed || it == DownloadState.Unverified }
        }

        val failed = finalState as DownloadState.Failed
        assertEquals(OtaErrorCategory.Network, failed.category)
        assertEquals(0, failed.retriesRemaining)
        assertTrue(failed.raw?.contains("Incomplete download") == true)
        assertTrue(promoter.promotions.isEmpty())
        assertTrue(store.finalPaths.isEmpty())
        assertFalse(store.updates.any { it.state == DownloadState.Unverified })
    }

    @Test
    fun `checksum mismatch becomes failed state`() = runTest {
        server.enqueue(MockResponse(code = 200, body = "abc"))
        server.start()
        val tempRoot = testTempRoot("mismatch")
        val repository = RecordingPackageRepository()
        val engine = SimpleDownloadEngine(
            client = OkHttpClient(),
            tempRoot = tempRoot,
            scope = backgroundScope,
            packageRepository = repository,
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
        assertEquals("00000000000000000000000000000000", failed.expectedHash)
        assertEquals("900150983cd24fb0d6963f7d28e17f72", failed.actualHash)
        assertTrue(failed.raw?.contains("expected 00000000000000000000000000000000") == true)
        assertEquals(
            listOf(ChecksumMismatchRecord("test", "00000000000000000000000000000000", "900150983cd24fb0d6963f7d28e17f72")),
            repository.checksumMismatches,
        )
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
    fun `rejects enqueue when admission gate blocks new downloads`() = runTest {
        server.enqueue(MockResponse(code = 200, body = "abc"))
        server.start()
        val store = RecordingDownloadTaskStore()
        val admissionGate = MutableDownloadAdmissionGate("Storage pressure is critical.")
        val engine = SimpleDownloadEngine(
            client = OkHttpClient(),
            tempRoot = testTempRoot("admission-rejected"),
            scope = backgroundScope,
            taskStore = store,
            admissionGate = admissionGate,
            idGenerator = { "rejected" },
        )

        val task = engine.enqueue(
            samplePackage(
                url = server.url("/pkg.zip").toString(),
                md5 = "900150983cd24fb0d6963f7d28e17f72",
            ),
        )

        val failed = task.state.first() as DownloadState.Failed
        assertEquals(OtaErrorCategory.File, failed.category)
        assertEquals(0, failed.retriesRemaining)
        assertEquals("Storage pressure is critical.", failed.raw)
        assertTrue(store.created.isEmpty())
        assertEquals(0, server.requestCount)
    }

    @Test
    fun `throttles small progress updates while downloading`() = runTest {
        server.enqueue(
            MockResponse.Builder()
                .code(200)
                .body("abc")
                .throttleBody(1, 10, TimeUnit.MILLISECONDS)
                .build(),
        )
        server.start()
        val store = RecordingDownloadTaskStore()
        val engine = SimpleDownloadEngine(
            client = OkHttpClient(),
            tempRoot = testTempRoot("progress-throttle"),
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

        val runningUpdates = store.updates
            .map { it.state }
            .filterIsInstance<DownloadState.Running>()
        assertEquals(listOf(0L), runningUpdates.map { it.downloadedBytes })
    }

    @Test
    fun `emits progress after each mebibyte downloaded`() = runTest {
        val body = "a".repeat(1024 * 1024) + "b"
        server.enqueue(MockResponse(code = 200, body = body))
        server.start()
        val store = RecordingDownloadTaskStore()
        val engine = SimpleDownloadEngine(
            client = OkHttpClient(),
            tempRoot = testTempRoot("progress-mebibyte"),
            scope = backgroundScope,
            taskStore = store,
        )

        val task = engine.enqueue(
            samplePackage(
                url = server.url("/pkg.zip").toString(),
                md5 = "b67a5f55dade2839f62150d7353fdf03",
            ),
        )

        withTimeout(5.seconds) {
            task.state.first { it == DownloadState.Verified }
        }

        val runningUpdates = store.updates
            .map { it.state }
            .filterIsInstance<DownloadState.Running>()
            .map { it.downloadedBytes }
        assertEquals(2, runningUpdates.size)
        assertEquals(0L, runningUpdates.first())
        assertTrue(runningUpdates.last() >= 1024L * 1024L)
    }

    @Test
    fun `emits download speed with progress updates`() = runTest {
        val body = "a".repeat(1024 * 1024) + "b"
        server.enqueue(MockResponse(code = 200, body = body))
        server.start()
        val store = RecordingDownloadTaskStore()
        val engine = SimpleDownloadEngine(
            client = OkHttpClient(),
            tempRoot = testTempRoot("progress-speed"),
            scope = backgroundScope,
            taskStore = store,
        )

        val task = engine.enqueue(
            samplePackage(
                url = server.url("/pkg.zip").toString(),
                md5 = "b67a5f55dade2839f62150d7353fdf03",
            ),
        )

        withTimeout(5.seconds) {
            task.state.first { it == DownloadState.Verified }
        }

        val speeds = store.updates
            .map { it.state }
            .filterIsInstance<DownloadState.Running>()
            .mapNotNull { it.speedBytesPerSec }
        assertTrue(speeds.any { it > 0L })
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
        val packageRepository = RecordingPackageRepository()
        val tempRoot = testTempRoot("promoted")
        val engine = SimpleDownloadEngine(
            client = OkHttpClient(),
            tempRoot = tempRoot,
            scope = backgroundScope,
            taskStore = store,
            filePromoter = promoter,
            packageRepository = packageRepository,
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
        assertEquals("test", packageRepository.downloaded.single().packageName)
        assertEquals("content://downloads/pkg.zip", packageRepository.downloaded.single().localFilePath)
        assertTrue(packageRepository.downloaded.single().downloadedAtMs > 0L)
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
        val retryDelays = mutableListOf<Int>()
        val engine = SimpleDownloadEngine(
            client = OkHttpClient(),
            tempRoot = testTempRoot("server-retry"),
            scope = backgroundScope,
            taskStore = store,
            retryDelay = { attempt ->
                retryDelays += attempt
                assertEquals(1, server.requestCount)
            },
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
        assertEquals(listOf(1), retryDelays)
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
    fun `retry resumes from persisted metadata after partial transfer failure`() = runTest {
        val requestRanges = mutableListOf<String?>()
        var requestCount = 0
        val client = OkHttpClient.Builder()
            .addInterceptor { chain ->
                requestCount += 1
                val request = chain.request()
                val range = request.header("Range")
                requestRanges += range
                val responseCode = if (range == "bytes=2-") 206 else 200
                val responseBody = when (requestCount) {
                    1 -> FailingResponseBody(bytesBeforeFailure = "ab", declaredLength = 3L)
                    else -> if (range == "bytes=2-") "c".toResponseBody() else "abc".toResponseBody()
                }
                Response.Builder()
                    .request(request)
                    .protocol(Protocol.HTTP_1_1)
                    .code(responseCode)
                    .message("OK")
                    .header("Accept-Ranges", "bytes")
                    .header("ETag", "\"abc\"")
                    .body(responseBody)
                    .build()
            }
            .build()
        val tempRoot = testTempRoot("retry-resume-metadata")
        val store = RecordingDownloadTaskStore()
        val engine = SimpleDownloadEngine(
            client = client,
            tempRoot = tempRoot,
            scope = backgroundScope,
            taskStore = store,
            retryDelay = {},
        )

        val task = engine.enqueue(
            OtaPackage(
                versionName = "test",
                type = "full",
                sizeBytes = 3L,
                sourceHost = "example.test",
                downloadUrl = "https://example.test/pkg.zip",
                md5 = "900150983cd24fb0d6963f7d28e17f72",
            ),
        )

        withTimeout(5.seconds) {
            task.state.first { it == DownloadState.Verified }
        }

        assertEquals(listOf(null, "bytes=2-"), requestRanges)
        assertEquals("abc", tempRoot.resolve("${task.taskId}.zip.part").readText())
        assertEquals(true, store.resumeMetadata.last().acceptRanges)
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
    fun `stopStoredTask cancels active persisted download without verifying partial file`() = runTest {
        val body = "abcdef"
        server.enqueue(
            MockResponse.Builder()
                .code(200)
                .body(body)
                .bodyDelay(1, TimeUnit.SECONDS)
                .build(),
        )
        server.start()
        val tempRoot = testTempRoot("stop-stored-task")
        val tempFile = tempRoot.resolve("task-1.zip.part")
        val pkg = samplePackage(
            url = server.url("/pkg.zip").toString(),
            md5 = "e80b5017098950fc58aad83c8c14978e",
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

        val execution = async(Dispatchers.IO) { engine.executeStoredTask("task-1") }
        withTimeout(5.seconds) {
            while (store.updates.none { it.state is DownloadState.Running }) {
                Thread.sleep(10)
            }
        }
        engine.stopStoredTask("task-1")

        val finalState = withTimeout(5.seconds) { execution.await() }

        assertTrue(finalState is DownloadState.Running)
        assertTrue(store.updates.none { it.state == DownloadState.Verifying })
        assertTrue(store.updates.none { it.state == DownloadState.Verified })
        assertTrue(tempFile.length() < body.length)
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
    fun `execute stored user paused task returns paused state without downloading`() = runTest {
        server.enqueue(MockResponse(code = 200, body = "abc"))
        server.start()
        val tempRoot = testTempRoot("execute-stored-user-paused-task")
        val paused = DownloadState.Paused(DownloadState.Paused.PauseReason.User)
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
                    finalFilePath = null,
                    etag = null,
                    lastModified = null,
                    acceptRanges = false,
                    state = paused,
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

        assertEquals(paused, finalState)
        assertEquals(0, server.requestCount)
        assertTrue(store.updates.isEmpty())
    }

    @Test
    fun `execute stored task verifies complete partial without redownloading`() = runTest {
        server.enqueue(MockResponse(code = 416))
        server.enqueue(MockResponse(code = 200, body = "abc"))
        server.start()
        val tempRoot = testTempRoot("execute-stored-complete-partial")
        val tempFile = tempRoot.resolve("task-1.zip.part")
        tempFile.writeText("abc")
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
                    state = DownloadState.Running(3L, 3L, null),
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
        assertEquals(DownloadState.Verified, store.updates.last().state)
    }

    @Test
    fun `execute stored task reuses existing in memory task row`() = runTest {
        server.enqueue(MockResponse(code = 200, body = "abc"))
        server.start()
        val tempRoot = testTempRoot("execute-stored-task-deduplicate")
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

        engine.executeStoredTask("task-1")
        engine.executeStoredTask("task-1")

        assertEquals(
            listOf("task-1"),
            engine.observeAll().first().map { it.taskId },
        )
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
    fun `rejects enqueue when active queue reaches limit`() = runTest {
        val engineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        try {
            server.enqueue(
                MockResponse.Builder()
                    .code(200)
                    .body("abc")
                    .bodyDelay(1, TimeUnit.SECONDS)
                    .build(),
            )
            server.start()
            val store = RecordingDownloadTaskStore()
            val engine = SimpleDownloadEngine(
                client = OkHttpClient(),
                tempRoot = testTempRoot("queue-limit"),
                scope = engineScope,
                taskStore = store,
                maxQueuedTasks = 1,
            )

            engine.enqueue(
                samplePackage(
                    url = server.url("/first.zip").toString(),
                    md5 = "900150983cd24fb0d6963f7d28e17f72",
                ),
            )
            val rejected = engine.enqueue(
                samplePackage(
                    url = server.url("/second.zip").toString(),
                    md5 = "900150983cd24fb0d6963f7d28e17f72",
                ),
            )

            val failed = rejected.state.first() as DownloadState.Failed
            assertEquals(OtaErrorCategory.File, failed.category)
            assertEquals(0, failed.retriesRemaining)
            assertTrue(failed.raw?.contains("queue limit") == true)
            assertEquals(1, store.created.size)
            waitUntilRequestCount(1)
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
    fun `pause stops active download without verifying partial file`() = runTest {
        val engineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        try {
            val body = "abcdef"
            server.enqueue(
                MockResponse.Builder()
                    .code(200)
                    .body(body)
                    .bodyDelay(1, TimeUnit.SECONDS)
                    .build(),
            )
            server.start()
            val tempRoot = testTempRoot("pause-active")
            val store = RecordingDownloadTaskStore()
            val engine = SimpleDownloadEngine(
                client = OkHttpClient(),
                tempRoot = tempRoot,
                scope = engineScope,
                taskStore = store,
            )
            val task = engine.enqueue(
                samplePackage(
                    url = server.url("/pkg.zip").toString(),
                    md5 = "e80b5017098950fc58aad83c8c14978e",
                ),
            )

            task.state.first { it is DownloadState.Running }
            task.pause()

            assertEquals(
                DownloadState.Paused(DownloadState.Paused.PauseReason.User),
                task.state.first(),
            )
            Thread.sleep(500)
            assertEquals(
                DownloadState.Paused(DownloadState.Paused.PauseReason.User),
                task.state.first(),
            )
            assertTrue(store.updates.none { it.state == DownloadState.Verifying })
            assertTrue(store.updates.none { it.state == DownloadState.Verified })
            assertTrue(tempRoot.resolve("${task.taskId}.zip.part").length() < body.length)
        } finally {
            engineScope.cancel()
        }
    }

    @Test
    fun `resume restarts paused active download`() = runTest {
        val engineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        try {
            server.enqueue(
                MockResponse.Builder()
                    .code(200)
                    .body("interrupted")
                    .bodyDelay(1, TimeUnit.SECONDS)
                    .build(),
            )
            server.enqueue(MockResponse(code = 200, body = "abc"))
            server.start()
            val store = RecordingDownloadTaskStore()
            val engine = SimpleDownloadEngine(
                client = OkHttpClient(),
                tempRoot = testTempRoot("resume-paused-active"),
                scope = engineScope,
                taskStore = store,
            )
            val task = engine.enqueue(
                samplePackage(
                    url = server.url("/pkg.zip").toString(),
                    md5 = "900150983cd24fb0d6963f7d28e17f72",
                ),
            )

            task.state.first { it is DownloadState.Running }
            task.pause()
            task.resume()

            waitUntilVerified(task)

            assertEquals(2, server.requestCount)
            assertTrue(store.updates.any { it.state == DownloadState.Queued })
            assertEquals(DownloadState.Verified, store.updates.last().state)
        } finally {
            engineScope.cancel()
        }
    }

    @Test
    fun `queued task ignores pause until it starts`() = runTest {
        val engineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        try {
            server.enqueue(
                MockResponse.Builder()
                    .code(200)
                    .body("abc")
                    .bodyDelay(1, TimeUnit.SECONDS)
                    .build(),
            )
            server.enqueue(MockResponse(code = 200, body = "abc"))
            server.start()
            val engine = SimpleDownloadEngine(
                client = OkHttpClient(),
                tempRoot = testTempRoot("pause-queued"),
                scope = engineScope,
            )
            engine.enqueue(
                samplePackage(
                    url = server.url("/first.zip").toString(),
                    md5 = "900150983cd24fb0d6963f7d28e17f72",
                ),
            )
            val second = engine.enqueue(
                samplePackage(
                    url = server.url("/second.zip").toString(),
                    md5 = "900150983cd24fb0d6963f7d28e17f72",
                ),
            )

            second.pause()

            assertEquals(DownloadState.Queued, second.state.first())
        } finally {
            engineScope.cancel()
        }
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
    fun `resumed unknown-size short 206 uses content range total before unverified promotion`() = runTest {
        server.enqueue(
            MockResponse(
                code = 206,
                body = "c",
                headers = Headers.Builder()
                    .add("Content-Length", "1")
                    .add("Content-Range", "bytes 2-2/4")
                    .add("ETag", "\"abc\"")
                    .add("Accept-Ranges", "bytes")
                    .build(),
            ),
        )
        server.start()
        val tempRoot = testTempRoot("short-resumed-unknown-size")
        val tempFile = tempRoot.resolve("task-1.zip.part")
        tempFile.writeText("ab")
        val pkg = samplePackage(
            url = server.url("/pkg.zip").toString(),
            md5 = null,
        ).copy(sizeBytes = 0L)
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
                    state = DownloadState.Running(2L, null, null),
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
            maxAttempts = 1,
        )

        val task = engine.enqueue(pkg)

        val finalState = withTimeout(5.seconds) {
            task.state.first { it is DownloadState.Failed || it == DownloadState.Unverified }
        }

        val failed = finalState as DownloadState.Failed
        assertEquals(OtaErrorCategory.Network, failed.category)
        assertEquals(0, failed.retriesRemaining)
        assertTrue(failed.raw?.contains("expected 4 bytes, got 3") == true)
        assertEquals("bytes=2-", server.takeRequest().headers["Range"])
        assertFalse(store.updates.any { it.state == DownloadState.Unverified })
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
        assertTrue(
            store.updates.any { update ->
                val failed = update.state as? DownloadState.Failed
                failed?.category == OtaErrorCategory.Server &&
                    failed.raw == "Server ignored resume range, restarting download from zero"
            },
        )
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
        assertTrue(
            store.updates.any { update ->
                val failed = update.state as? DownloadState.Failed
                failed?.category == OtaErrorCategory.Server &&
                    failed.raw == "Server rejected resume range, restarting download from zero"
            },
        )
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
        assertTrue(
            store.updates.any { update ->
                val failed = update.state as? DownloadState.Failed
                failed?.category == OtaErrorCategory.Server &&
                    failed.raw == "Server changed package, restarting download from zero"
            },
        )
    }

    private fun samplePackage(
        url: String,
        md5: String?,
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

    private suspend fun waitUntilVerified(task: DownloadTask) {
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5)
        while (System.nanoTime() < deadline) {
            if (task.state.first() == DownloadState.Verified) return
            Thread.sleep(25)
        }
        assertEquals(DownloadState.Verified, task.state.first())
    }

    private fun waitUntilRequestCount(expected: Int) {
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5)
        while (System.nanoTime() < deadline) {
            if (server.requestCount >= expected) return
            Thread.sleep(10)
        }
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

    private class FailingResponseBody(
        private val bytesBeforeFailure: String,
        private val declaredLength: Long,
    ) : ResponseBody() {
        override fun contentType(): MediaType? = null

        override fun contentLength(): Long = declaredLength

        override fun source(): BufferedSource {
            val source = object : Source {
                private val bytes = Buffer().writeUtf8(bytesBeforeFailure)
                private var failed = false

                override fun read(sink: Buffer, byteCount: Long): Long {
                    if (bytes.size > 0L) {
                        return bytes.read(sink, byteCount)
                    }
                    if (!failed) {
                        failed = true
                        throw IOException("network dropped mid-body")
                    }
                    return -1L
                }

                override fun timeout(): Timeout = Timeout.NONE

                override fun close() = Unit
            }
            return source.buffer()
        }
    }

    private class RecordingPackageRepository : PackageRepository {
        val downloaded = mutableListOf<DownloadedPackage>()
        val checksumMismatches = mutableListOf<ChecksumMismatchRecord>()

        override suspend fun record(entry: HistoryEntry) = Unit

        override suspend fun markDownloaded(
            packageName: String,
            sourceHost: String,
            downloadUrl: String,
            downloadedAtMs: Long,
            localFilePath: String,
        ) {
            downloaded += DownloadedPackage(
                packageName = packageName,
                downloadedAtMs = downloadedAtMs,
                localFilePath = localFilePath,
            )
        }

        override suspend fun markChecksumMismatch(
            packageName: String,
            sourceHost: String,
            downloadUrl: String,
            expectedHash: String,
            actualHash: String,
        ) {
            checksumMismatches += ChecksumMismatchRecord(packageName, expectedHash, actualHash)
        }

        override fun observeHistory(): Flow<List<HistoryEntry>> = flowOf(emptyList())
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

    private data class DownloadedPackage(
        val packageName: String,
        val downloadedAtMs: Long,
        val localFilePath: String,
    )

    private data class ChecksumMismatchRecord(
        val packageName: String,
        val expectedHash: String,
        val actualHash: String,
    )
}
