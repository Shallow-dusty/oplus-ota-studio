package dev.shallowdusty.oplusotastudio.download

import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class DownloadForegroundManifestTest {

    @Test
    fun `declares permissions required for foreground data sync downloads`() {
        val manifest = manifest()
        val permissions = manifest.getElementsByTagName("uses-permission")
        val names = (0 until permissions.length).map { index ->
            permissions.item(index).attributes.getNamedItem("android:name").nodeValue
        }

        assertTrue("android.permission.FOREGROUND_SERVICE" in names)
        assertTrue("android.permission.FOREGROUND_SERVICE_DATA_SYNC" in names)
        assertTrue("android.permission.POST_NOTIFICATIONS" in names)
    }

    @Test
    fun `declares WorkManager foreground service as data sync`() {
        val manifest = manifest()
        val services = manifest.getElementsByTagName("service")
        val service = (0 until services.length)
            .map { services.item(it) }
            .single {
                it.attributes.getNamedItem("android:name").nodeValue ==
                    "androidx.work.impl.foreground.SystemForegroundService"
            }

        assertEquals(
            "dataSync",
            service.attributes.getNamedItem("android:foregroundServiceType").nodeValue,
        )
    }

    private fun manifest() =
        DocumentBuilderFactory.newInstance()
            .newDocumentBuilder()
            .parse(File("src/main/AndroidManifest.xml"))
}
