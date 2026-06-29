package dev.shallowdusty.oplusotastudio

import dev.shallowdusty.oplusotastudio.core.download.DownloadFilePromoter
import dev.shallowdusty.oplusotastudio.core.download.PromotedDownloadFile
import dev.shallowdusty.oplusotastudio.core.download.SimpleDownloadEngine
import dev.shallowdusty.oplusotastudio.core.model.DownloadState
import dev.shallowdusty.oplusotastudio.core.model.DownloadTaskStore
import dev.shallowdusty.oplusotastudio.core.model.HistoryEntry
import dev.shallowdusty.oplusotastudio.core.model.OtaPackage
import dev.shallowdusty.oplusotastudio.core.model.PackageRepository
import dev.shallowdusty.oplusotastudio.core.model.StoredDownloadTask
import dev.shallowdusty.oplusotastudio.core.ota.LegacyOtaLookupService
import dev.shallowdusty.oplusotastudio.device.AndroidDeviceDetector
import java.io.File
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertEquals
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
    fun `passes supplied download task store to real download engine`() = runTest {
        val store = RecordingDownloadTaskStore()
        val graph = AppGraph(
            downloadTempRoot = File("build/tmp/app-graph-store-test"),
            downloadTaskStore = store,
        )

        val task = graph.downloadEngine.enqueue(
            OtaPackage(
                versionName = "test",
                type = "full",
                sizeBytes = 3L,
                sourceHost = "127.0.0.1",
                downloadUrl = "http://127.0.0.1:1/pkg.zip",
                md5 = null,
            ),
        )

        assertEquals(task.taskId, store.created.single())
    }

    @Test
    fun `passes supplied download file promoter to real download engine`() {
        val promoter = NoOpDownloadFilePromoter()
        val graph = AppGraph(
            downloadTempRoot = File("build/tmp/app-graph-promoter-test"),
            downloadFilePromoter = promoter,
        )

        val engine = assertInstanceOf(SimpleDownloadEngine::class.java, graph.downloadEngine)
        val field = SimpleDownloadEngine::class.java.getDeclaredField("filePromoter")
            .apply { isAccessible = true }

        assertSame(promoter, field.get(engine))
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

    private class NoOpDownloadFilePromoter : DownloadFilePromoter {
        override suspend fun promote(
            taskId: String,
            pkg: OtaPackage,
            sourceFile: File,
        ): PromotedDownloadFile = PromotedDownloadFile(sourceFile.absolutePath)
    }

    private class RecordingDownloadTaskStore : DownloadTaskStore {
        val created = mutableListOf<String>()

        override suspend fun createQueuedTask(
            taskId: String,
            pkg: OtaPackage,
            tempFilePath: String,
            updatedAtMs: Long,
        ) {
            created += taskId
        }

        override suspend fun updateState(
            taskId: String,
            state: DownloadState,
            updatedAtMs: Long,
        ) = Unit

        override suspend fun updateResumeMetadata(
            taskId: String,
            etag: String?,
            lastModified: String?,
            acceptRanges: Boolean,
            updatedAtMs: Long,
        ) = Unit

        override suspend fun updateFinalFilePath(
            taskId: String,
            finalFilePath: String,
            updatedAtMs: Long,
        ) = Unit

        override suspend fun getTask(taskId: String): StoredDownloadTask? = null

        override fun observeTasks(): Flow<List<StoredDownloadTask>> = flowOf(emptyList())
    }
}
