package dev.shallowdusty.oplusotastudio.download

import dev.shallowdusty.oplusotastudio.core.download.DownloadFilePromoter
import dev.shallowdusty.oplusotastudio.core.download.PromotedDownloadFile
import dev.shallowdusty.oplusotastudio.core.download.SimpleDownloadEngine
import dev.shallowdusty.oplusotastudio.core.model.DownloadState
import dev.shallowdusty.oplusotastudio.core.model.OtaLookupResult
import dev.shallowdusty.oplusotastudio.core.model.OtaPackage
import dev.shallowdusty.oplusotastudio.core.ota.LegacyOtaProtocol
import java.io.File
import java.security.MessageDigest
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.OkHttpClient
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class OtaLookupDownloadSmokeTest {

    private val server = MockWebServer()

    @AfterEach
    fun tearDown() {
        server.close()
    }

    @Test
    fun `controlled OTA fixture parses and downloads to promoted file`() = runTest {
        val packageBytes = "controlled-ota-payload".toByteArray()
        server.enqueue(MockResponse(code = 200, body = packageBytes.decodeToString()))
        server.start()
        val rawXml = fixture("synthetic-e2e-legacy-oneplus9pro-cn-success.xml")
            .replace("{{sizeBytes}}", packageBytes.size.toString())
            .replace("{{md5}}", md5Hex(packageBytes))
            .replace("{{downloadUrl}}", "https://updates.example.test/oneplus9pro-cn-full.zip")

        val lookup = LegacyOtaProtocol().parseResponse(
            rawXml = rawXml,
            sourceHost = "controlled.local",
        )
        val parsedPkg = assertInstanceOf(OtaLookupResult.PackageFound::class.java, lookup).pkg
        val pkg = parsedPkg.copy(downloadUrl = server.url("/oneplus9pro-cn-full.zip").toString())
        val tempRoot = testTempRoot("temp")
        val finalRoot = testTempRoot("final")
        val promoter = CopyingDownloadFilePromoter(finalRoot)
        val engine = SimpleDownloadEngine(
            client = OkHttpClient(),
            tempRoot = tempRoot,
            scope = backgroundScope,
            filePromoter = promoter,
        )

        val task = engine.enqueue(pkg)

        withTimeout(5.seconds) {
            task.state.first { it == DownloadState.Verified }
        }
        val finalFile = finalRoot.resolve("LE2120_14.0.0.1901_CN01_full.zip")
        assertEquals("/oneplus9pro-cn-full.zip", server.takeRequest().url.encodedPath)
        assertEquals("controlled-ota-payload", finalFile.readText())
        assertEquals(finalFile.absolutePath, promoter.promotedPath)
        assertFalse(tempRoot.resolve("${task.taskId}.zip.part").exists())
    }

    private fun fixture(name: String): String =
        requireNotNull(javaClass.getResource("/fixtures/$name")) {
            "Missing OTA fixture: $name"
        }.readText()

    private fun testTempRoot(name: String): File {
        val dir = File("build/tmp/ota-lookup-download-smoke-test/$name")
        dir.deleteRecursively()
        assertTrue(dir.mkdirs())
        return dir
    }

    private fun md5Hex(bytes: ByteArray): String =
        MessageDigest.getInstance("MD5")
            .digest(bytes)
            .joinToString("") { "%02x".format(it) }

    private class CopyingDownloadFilePromoter(
        private val finalRoot: File,
    ) : DownloadFilePromoter {
        var promotedPath: String? = null
            private set

        override suspend fun promote(
            taskId: String,
            pkg: OtaPackage,
            sourceFile: File,
        ): PromotedDownloadFile {
            val target = DownloadPromotionTarget.fromPackage(pkg)
            val destination = finalRoot.resolve(target.displayName)
            sourceFile.copyTo(destination, overwrite = true)
            promotedPath = destination.absolutePath
            return PromotedDownloadFile(destination.absolutePath)
        }
    }
}
