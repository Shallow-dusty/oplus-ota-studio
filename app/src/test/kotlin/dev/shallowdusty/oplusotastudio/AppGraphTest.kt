package dev.shallowdusty.oplusotastudio

import dev.shallowdusty.oplusotastudio.core.download.SimpleDownloadEngine
import dev.shallowdusty.oplusotastudio.core.ota.LegacyOtaLookupService
import dev.shallowdusty.oplusotastudio.device.AndroidDeviceDetector
import java.io.File
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Test

class AppGraphTest {

    @Test
    fun `uses real OTA lookup service`() {
        val graph = AppGraph()

        assertInstanceOf(LegacyOtaLookupService::class.java, graph.otaLookupService)
    }

    @Test
    fun `uses real device detector`() {
        val graph = AppGraph()

        assertInstanceOf(AndroidDeviceDetector::class.java, graph.deviceDetector)
    }

    @Test
    fun `uses real download engine when temp root is provided`() {
        val graph = AppGraph(downloadTempRoot = File("build/tmp/app-graph-test"))

        assertInstanceOf(SimpleDownloadEngine::class.java, graph.downloadEngine)
    }
}
