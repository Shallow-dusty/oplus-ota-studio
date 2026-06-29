package dev.shallowdusty.oplusotastudio.core.download

import dev.shallowdusty.oplusotastudio.core.model.DownloadState
import dev.shallowdusty.oplusotastudio.core.model.OtaErrorCategory
import dev.shallowdusty.oplusotastudio.core.model.OtaPackage
import java.io.File
import kotlin.time.Duration.Companion.seconds
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
}
