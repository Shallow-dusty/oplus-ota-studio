package dev.shallowdusty.oplusotastudio.download

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.shallowdusty.oplusotastudio.core.download.DownloadFilePromoter
import dev.shallowdusty.oplusotastudio.core.download.PromotedDownloadFile
import dev.shallowdusty.oplusotastudio.core.download.SimpleDownloadEngine
import dev.shallowdusty.oplusotastudio.core.model.DownloadState
import dev.shallowdusty.oplusotastudio.core.model.OtaLookupResult
import dev.shallowdusty.oplusotastudio.core.model.OtaPackage
import dev.shallowdusty.oplusotastudio.core.ota.LegacyOtaProtocol
import java.io.File
import java.io.FileInputStream
import java.security.MessageDigest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AndroidOtaLookupDownloadSmokeInstrumentedTest {

    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()
    private val engineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val createdUris = mutableListOf<Uri>()
    private val createdFiles = mutableListOf<File>()

    @After
    fun tearDown() {
        engineScope.cancel()
        createdUris.forEach { uri ->
            context.contentResolver.delete(uri, null, null)
        }
        createdFiles.forEach { file ->
            file.delete()
        }
    }

    @Test
    fun parsesControlledOtaFixtureAndPromotesDownloadedZip() = runBlocking {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            grantWriteExternalStorage()
        }
        val packageBytes = "android-runtime-ota-payload".toByteArray()
        val requestedUrls = mutableListOf<String>()
        val client = OkHttpClient.Builder()
            .addInterceptor { chain ->
                requestedUrls += chain.request().url.toString()
                Response.Builder()
                    .request(chain.request())
                    .protocol(Protocol.HTTP_1_1)
                    .code(200)
                    .message("OK")
                    .body(packageBytes.toResponseBody("application/zip".toMediaType()))
                    .build()
            }
            .build()
        val downloadUrl = "https://updates.example.test/oneplus9pro-cn-full.zip"
        val lookup = LegacyOtaProtocol().parseResponse(
            rawXml = controlledSuccessXml(
                sizeBytes = packageBytes.size,
                md5 = md5Hex(packageBytes),
                downloadUrl = downloadUrl,
            ),
            sourceHost = "controlled.local",
        )
        assertTrue(lookup is OtaLookupResult.PackageFound)
        val pkg = (lookup as OtaLookupResult.PackageFound).pkg
        val promoter = RecordingDownloadFilePromoter(AndroidMediaStoreDownloadFilePromoter(context))
        val tempRoot = File(context.cacheDir, "android-ota-download-smoke").apply {
            deleteRecursively()
            assertTrue(mkdirs())
        }
        val engine = SimpleDownloadEngine(
            client = client,
            tempRoot = tempRoot,
            scope = engineScope,
            filePromoter = promoter,
        )

        val task = engine.enqueue(pkg)

        withTimeout(10_000) {
            task.state.first { it == DownloadState.Verified }
        }
        assertEquals(listOf(downloadUrl), requestedUrls)
        val finalPath = promoter.finalFilePath
        assertNotNull(finalPath)
        assertPromotedFile(finalPath!!, "android-runtime-ota-payload")
        assertTrue(tempRoot.listFiles().orEmpty().none { it.name.endsWith(".zip.part") })
    }

    private fun controlledSuccessXml(
        sizeBytes: Int,
        md5: String,
        downloadUrl: String,
    ): String =
        """
        <root>
          <Command>NEW_VERSION</Command>
          <versionName>LE2120_14.0.0.1901(CN01)</versionName>
          <type>full</type>
          <size>$sizeBytes</size>
          <md5>$md5</md5>
          <url>$downloadUrl</url>
        </root>
        """.trimIndent()

    private fun assertPromotedFile(path: String, expectedBody: String) {
        if (path.startsWith("content://")) {
            val uri = Uri.parse(path).also(createdUris::add)
            val body = context.contentResolver.openInputStream(uri)?.use { input ->
                input.readBytes().decodeToString()
            }
            assertEquals(expectedBody, body)
        } else {
            val file = File(path).also(createdFiles::add)
            assertTrue(file.exists())
            assertEquals(expectedBody, file.readText())
        }
    }

    private fun grantWriteExternalStorage() {
        InstrumentationRegistry.getInstrumentation().uiAutomation
            .executeShellCommand("pm grant ${context.packageName} ${Manifest.permission.WRITE_EXTERNAL_STORAGE}")
            .use { descriptor ->
                FileInputStream(descriptor.fileDescriptor).bufferedReader().use { reader ->
                    reader.readText()
                }
            }
        assertEquals(
            PackageManager.PERMISSION_GRANTED,
            context.checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE),
        )
    }

    private fun md5Hex(bytes: ByteArray): String =
        MessageDigest.getInstance("MD5")
            .digest(bytes)
            .joinToString("") { "%02x".format(it) }

    private class RecordingDownloadFilePromoter(
        private val delegate: DownloadFilePromoter,
    ) : DownloadFilePromoter {
        var finalFilePath: String? = null
            private set

        override suspend fun promote(
            taskId: String,
            pkg: OtaPackage,
            sourceFile: File,
        ): PromotedDownloadFile =
            delegate.promote(taskId, pkg, sourceFile)
                .also { finalFilePath = it.finalFilePath }
    }
}
