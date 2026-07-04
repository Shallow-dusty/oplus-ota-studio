package dev.shallowdusty.oplusotastudio.download

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.shallowdusty.oplusotastudio.core.model.OtaPackage
import java.io.File
import java.io.FileInputStream
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AndroidMediaStoreDownloadFilePromoterInstrumentedTest {

    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()
    private val createdUris = mutableListOf<Uri>()
    private val createdFiles = mutableListOf<File>()

    @After
    fun tearDown() {
        createdUris.forEach { uri ->
            context.contentResolver.delete(uri, null, null)
        }
        createdFiles.forEach { file ->
            file.delete()
        }
    }

    @Test
    fun promotesZipIntoDownloadsCollectionOnScopedStorage() = runBlocking {
        assumeTrue(Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
        val source = File(context.cacheDir, "instrumented-ota.zip").apply {
            writeText("ota-bytes")
        }
        val promoter = AndroidMediaStoreDownloadFilePromoter(context)

        val promoted = promoter.promote(
            taskId = "instrumented-task",
            pkg = OtaPackage(
                versionName = "LE2120_14.0.0.1901(CN01)",
                type = "full",
                sizeBytes = source.length(),
                sourceHost = "otacn.oppo.com",
                downloadUrl = "https://example.invalid/package.zip",
                md5 = null,
            ),
            sourceFile = source,
        )

        val uri = Uri.parse(promoted.finalFilePath).also(createdUris::add)
        val query = context.contentResolver.query(
            uri,
            arrayOf(
                MediaStore.MediaColumns.DISPLAY_NAME,
                MediaStore.MediaColumns.RELATIVE_PATH,
                MediaStore.MediaColumns.SIZE,
            ),
            null,
            null,
            null,
        )

        assertNotNull(query)
        query.use { cursor ->
            assertTrue(cursor!!.moveToFirst())
            assertEquals("LE2120_14.0.0.1901_CN01_full.zip", cursor.getString(0))
            assertEquals("Download/OPlus OTA Studio/", cursor.getString(1))
            assertEquals(source.length(), cursor.getLong(2))
        }
        val bytes = context.contentResolver.openInputStream(uri)?.use { input ->
            input.readBytes().decodeToString()
        }
        assertEquals("ota-bytes", bytes)
    }

    @Test
    fun promotesZipIntoPublicDownloadsOnLegacyStorage() = runBlocking {
        assumeTrue(Build.VERSION.SDK_INT < Build.VERSION_CODES.Q)
        grantWriteExternalStorage()
        val source = File(context.cacheDir, "instrumented-legacy-ota.zip").apply {
            writeText("legacy-ota-bytes")
        }
        val promoter = AndroidMediaStoreDownloadFilePromoter(context)

        val promoted = promoter.promote(
            taskId = "instrumented-legacy-task",
            pkg = OtaPackage(
                versionName = "LE2120_14.0.0.1901(CN01)",
                type = "full",
                sizeBytes = source.length(),
                sourceHost = "otacn.oppo.com",
                downloadUrl = "https://example.invalid/package.zip",
                md5 = null,
            ),
            sourceFile = source,
        )

        val destination = File(promoted.finalFilePath).also(createdFiles::add)
        val expectedDir = Environment
            .getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            .resolve("OPlus OTA Studio")

        assertTrue(destination.exists())
        assertEquals(expectedDir.absolutePath, destination.parentFile?.absolutePath)
        assertEquals("LE2120_14.0.0.1901_CN01_full.zip", destination.name)
        assertEquals("legacy-ota-bytes", destination.readText())
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
}
