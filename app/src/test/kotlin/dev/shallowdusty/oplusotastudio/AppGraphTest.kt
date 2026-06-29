package dev.shallowdusty.oplusotastudio

import dev.shallowdusty.oplusotastudio.core.download.SimpleDownloadEngine
import dev.shallowdusty.oplusotastudio.core.model.HistoryEntry
import dev.shallowdusty.oplusotastudio.core.model.PackageRepository
import dev.shallowdusty.oplusotastudio.core.ota.LegacyOtaLookupService
import dev.shallowdusty.oplusotastudio.device.AndroidDeviceDetector
import java.io.File
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertSame
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

    @Test
    fun `uses supplied package repository`() {
        val repository = RecordingPackageRepository()
        val graph = AppGraph(packageRepository = repository)

        assertSame(repository, graph.packageRepository)
    }

    private class RecordingPackageRepository : PackageRepository {
        override suspend fun record(entry: HistoryEntry) = Unit

        override fun observeHistory(): Flow<List<HistoryEntry>> = flowOf(emptyList())
    }
}
