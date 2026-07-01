package dev.shallowdusty.oplusotastudio

import androidx.work.OneTimeWorkRequest
import dev.shallowdusty.oplusotastudio.core.download.DownloadFilePromoter
import dev.shallowdusty.oplusotastudio.core.download.DownloadStorageSnapshot
import dev.shallowdusty.oplusotastudio.core.download.DownloadTempFileJanitor
import dev.shallowdusty.oplusotastudio.core.download.PromotedDownloadFile
import dev.shallowdusty.oplusotastudio.core.download.SimpleDownloadEngine
import dev.shallowdusty.oplusotastudio.core.model.DownloadPreferences
import dev.shallowdusty.oplusotastudio.core.model.DownloadPreferencesStore
import dev.shallowdusty.oplusotastudio.core.model.DownloadState
import dev.shallowdusty.oplusotastudio.core.model.DownloadTaskStore
import dev.shallowdusty.oplusotastudio.core.model.HistoryEntry
import dev.shallowdusty.oplusotastudio.core.model.OtaPackage
import dev.shallowdusty.oplusotastudio.core.model.PackageRepository
import dev.shallowdusty.oplusotastudio.core.model.StoredDownloadTask
import dev.shallowdusty.oplusotastudio.core.ota.LegacyOtaLookupService
import dev.shallowdusty.oplusotastudio.device.AndroidDeviceDetector
import dev.shallowdusty.oplusotastudio.download.DownloadWorkEnqueuer
import dev.shallowdusty.oplusotastudio.download.DownloadWorkScheduler
import dev.shallowdusty.oplusotastudio.download.DownloadTaskWorkScheduler
import dev.shallowdusty.oplusotastudio.download.DownloadWorkerExecutionResult
import dev.shallowdusty.oplusotastudio.download.DownloadWorkerExecutor
import dev.shallowdusty.oplusotastudio.download.SerialDownloadWorkerExecutor
import dev.shallowdusty.oplusotastudio.download.WorkScheduledDownloadEngine
import dev.shallowdusty.oplusotastudio.logging.AppDiagnosticsProvider
import dev.shallowdusty.oplusotastudio.logging.AppLogger
import dev.shallowdusty.oplusotastudio.logging.AppLogArchiveExporter
import dev.shallowdusty.oplusotastudio.logging.AppLogLevel
import dev.shallowdusty.oplusotastudio.logging.AppLogSink
import dev.shallowdusty.oplusotastudio.logging.LoggingDownloadEngine
import dev.shallowdusty.oplusotastudio.logging.LoggingOtaLookupService
import java.io.File
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class AppGraphTest {

    @Test
    fun `uses real OTA lookup service`() {
        val graph = AppGraph()

        val loggingService = assertInstanceOf(LoggingOtaLookupService::class.java, graph.otaLookupService)
        val delegateField = LoggingOtaLookupService::class.java.getDeclaredField("delegate")
            .apply { isAccessible = true }
        assertInstanceOf(LegacyOtaLookupService::class.java, delegateField.get(loggingService))
    }

    @Test
    fun `uses real device detector`() {
        val graph = AppGraph()

        assertInstanceOf(AndroidDeviceDetector::class.java, graph.deviceDetector)
    }

    @Test
    fun `uses real download engine when temp root is provided`() {
        val graph = AppGraph(downloadTempRoot = File("build/tmp/app-graph-test"))

        val loggingEngine = assertInstanceOf(LoggingDownloadEngine::class.java, graph.downloadEngine)
        val delegateField = LoggingDownloadEngine::class.java.getDeclaredField("delegate")
            .apply { isAccessible = true }
        assertInstanceOf(SimpleDownloadEngine::class.java, delegateField.get(loggingEngine))
    }

    @Test
    fun `uses work scheduled download engine when scheduler is provided`() {
        val graph = AppGraph(
            downloadTempRoot = File("build/tmp/app-graph-scheduled-engine-test"),
            downloadTaskStore = RecordingDownloadTaskStore(),
            downloadWorkScheduler = NoOpDownloadTaskWorkScheduler(),
        )

        val loggingEngine = assertInstanceOf(LoggingDownloadEngine::class.java, graph.downloadEngine)
        val delegateField = LoggingDownloadEngine::class.java.getDeclaredField("delegate")
            .apply { isAccessible = true }
        assertInstanceOf(WorkScheduledDownloadEngine::class.java, delegateField.get(loggingEngine))
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

        val loggingEngine = assertInstanceOf(LoggingDownloadEngine::class.java, graph.downloadEngine)
        val delegateField = LoggingDownloadEngine::class.java.getDeclaredField("delegate")
            .apply { isAccessible = true }
        val engine = assertInstanceOf(SimpleDownloadEngine::class.java, delegateField.get(loggingEngine))
        val field = SimpleDownloadEngine::class.java.getDeclaredField("filePromoter")
            .apply { isAccessible = true }

        assertSame(promoter, field.get(engine))
    }

    @Test
    fun `passes supplied storage snapshot provider to real download engine`() {
        val provider = {
            DownloadStorageSnapshot(
                tempAvailableBytes = Long.MAX_VALUE,
                finalAvailableBytes = Long.MAX_VALUE,
                tempAndFinalShareVolume = true,
            )
        }
        val graph = AppGraph(
            downloadTempRoot = File("build/tmp/app-graph-storage-preflight-test"),
            storageSnapshotProvider = provider,
        )

        val loggingEngine = assertInstanceOf(LoggingDownloadEngine::class.java, graph.downloadEngine)
        val delegateField = LoggingDownloadEngine::class.java.getDeclaredField("delegate")
            .apply { isAccessible = true }
        val engine = assertInstanceOf(SimpleDownloadEngine::class.java, delegateField.get(loggingEngine))
        val field = SimpleDownloadEngine::class.java.getDeclaredField("storageSnapshotProvider")
            .apply { isAccessible = true }

        assertSame(provider, field.get(engine))
    }

    @Test
    fun `uses supplied package repository`() {
        val repository = RecordingPackageRepository()
        val graph = AppGraph(packageRepository = repository)

        assertSame(repository, graph.packageRepository)
    }

    @Test
    fun `uses supplied download preferences store`() {
        val store = RecordingDownloadPreferencesStore()
        val graph = AppGraph(downloadPreferencesStore = store)

        assertSame(store, graph.downloadPreferencesStore)
    }

    @Test
    fun `uses supplied download work scheduler`() {
        val scheduler = DownloadWorkScheduler(
            preferencesStore = RecordingDownloadPreferencesStore(DownloadPreferences()),
            enqueuer = NoOpDownloadWorkEnqueuer(),
        )
        val graph = AppGraph(downloadWorkScheduler = scheduler)

        assertSame(scheduler, graph.downloadWorkScheduler)
    }

    @Test
    fun `uses supplied download worker executor`() {
        val executor = NoOpDownloadWorkerExecutor()
        val graph = AppGraph(downloadWorkerExecutor = executor)

        assertSame(executor, graph.downloadWorkerExecutor)
    }

    @Test
    fun `uses supplied app logger`() {
        val logger = AppLogger(
            sink = NoOpAppLogSink(),
            minLevel = AppLogLevel.Info,
        )
        val graph = AppGraph(appLogger = logger)

        assertSame(logger, graph.appLogger)
    }

    @Test
    fun `uses supplied app log archive exporter`() {
        val exporter = AppLogArchiveExporter(
            logStore = dev.shallowdusty.oplusotastudio.logging.RollingFileLogStore(
                logDir = File("build/tmp/app-graph-log-exporter-test"),
            ),
        )
        val graph = AppGraph(appLogArchiveExporter = exporter)

        assertSame(exporter, graph.appLogArchiveExporter)
    }

    @Test
    fun `uses supplied app diagnostics provider`() {
        val logStore = dev.shallowdusty.oplusotastudio.logging.RollingFileLogStore(
            logDir = File("build/tmp/app-graph-diagnostics-test"),
        )
        val provider = AppDiagnosticsProvider(
            logStore = logStore,
            logArchiveExporter = AppLogArchiveExporter(logStore = logStore),
        )
        val graph = AppGraph(appDiagnosticsProvider = provider)

        assertSame(provider, graph.appDiagnosticsProvider)
    }

    @Test
    fun `creates download worker executor for real download engine`() {
        val graph = AppGraph(downloadTempRoot = File("build/tmp/app-graph-worker-executor-test"))

        assertNotNull(graph.downloadWorkerExecutor)
    }

    @Test
    fun `serializes real download worker executor`() {
        val graph = AppGraph(downloadTempRoot = File("build/tmp/app-graph-serial-worker-executor-test"))

        assertInstanceOf(SerialDownloadWorkerExecutor::class.java, graph.downloadWorkerExecutor)
    }

    @Test
    fun `cleans orphaned download parts while keeping stored task parts`() = runTest {
        val tempRoot = testTempRoot("app-graph-janitor")
        val activePart = tempRoot.resolve("active.zip.part").also { it.writeText("active") }
        val orphanPart = tempRoot.resolve("orphan.zip.part").also { it.writeText("orphan") }
        val store = RecordingDownloadTaskStore(
            observedTasks = listOf(storedTask(tempFilePath = activePart.path)),
        )
        val graph = AppGraph(
            downloadTempRoot = tempRoot,
            downloadTaskStore = store,
            downloadTempFileJanitor = DownloadTempFileJanitor(listOf(tempRoot)),
        )

        val result = graph.cleanOrphanedDownloadParts()

        assertTrue(activePart.exists())
        assertFalse(orphanPart.exists())
        assertEquals(listOf(orphanPart.absolutePath), result?.deletedPaths)
    }

    @Test
    fun `startup maintenance cleans parts and reschedules recoverable downloads`() = runTest {
        val tempRoot = testTempRoot("app-graph-startup-maintenance")
        val activePart = tempRoot.resolve("active.zip.part").also { it.writeText("active") }
        val orphanPart = tempRoot.resolve("orphan.zip.part").also { it.writeText("orphan") }
        val store = RecordingDownloadTaskStore(
            observedTasks = listOf(
                storedTask(
                    tempFilePath = activePart.path,
                    state = DownloadState.Running(
                        downloadedBytes = 2L,
                        targetSize = 3L,
                        speedBytesPerSec = null,
                    ),
                ),
            ),
        )
        val scheduler = RecordingDownloadTaskWorkScheduler()
        val graph = AppGraph(
            downloadTempRoot = tempRoot,
            downloadTaskStore = store,
            downloadTempFileJanitor = DownloadTempFileJanitor(listOf(tempRoot)),
            downloadWorkScheduler = scheduler,
        )

        graph.runStartupMaintenance()

        assertFalse(orphanPart.exists())
        assertEquals(listOf("task-1"), scheduler.scheduled)
        assertEquals(DownloadState.Queued, store.updatedStates.single())
    }

    private class RecordingPackageRepository : PackageRepository {
        override suspend fun record(entry: HistoryEntry) = Unit

        override suspend fun markDownloaded(
            packageName: String,
            downloadedAtMs: Long,
            localFilePath: String,
        ) = Unit

        override fun observeHistory(): Flow<List<HistoryEntry>> = flowOf(emptyList())
    }

    private class NoOpDownloadFilePromoter : DownloadFilePromoter {
        override suspend fun promote(
            taskId: String,
            pkg: OtaPackage,
            sourceFile: File,
        ): PromotedDownloadFile = PromotedDownloadFile(sourceFile.absolutePath)
    }

    private class RecordingDownloadPreferencesStore : DownloadPreferencesStore {
        constructor() : this(DownloadPreferences())

        constructor(initialPreferences: DownloadPreferences) {
            state.value = initialPreferences
        }

        private val state = MutableStateFlow(DownloadPreferences())

        override val preferences: Flow<DownloadPreferences> =
            state

        override suspend fun setWifiOnly(enabled: Boolean) = Unit

        override suspend fun setBatteryPauseThresholdPercent(percent: Int) = Unit
    }

    private class NoOpDownloadWorkEnqueuer : DownloadWorkEnqueuer {
        override fun enqueue(taskId: String, request: OneTimeWorkRequest) = Unit

        override fun cancel(taskId: String) = Unit
    }

    private class NoOpDownloadTaskWorkScheduler : DownloadTaskWorkScheduler {
        override suspend fun schedule(taskId: String) = Unit

        override fun cancel(taskId: String) = Unit
    }

    private class RecordingDownloadTaskWorkScheduler : DownloadTaskWorkScheduler {
        val scheduled = mutableListOf<String>()

        override suspend fun schedule(taskId: String) {
            scheduled += taskId
        }

        override fun cancel(taskId: String) = Unit
    }

    private class NoOpDownloadWorkerExecutor : DownloadWorkerExecutor {
        override suspend fun execute(taskId: String): DownloadWorkerExecutionResult =
            DownloadWorkerExecutionResult.Failed
    }

    private class NoOpAppLogSink : AppLogSink {
        override fun append(level: AppLogLevel, tag: String, message: String, nowMs: Long) = Unit
    }

    private fun testTempRoot(name: String): File {
        val dir = File("build/tmp/$name")
        dir.deleteRecursively()
        dir.mkdirs()
        return dir
    }

    private fun storedTask(
        tempFilePath: String,
        state: DownloadState = DownloadState.Queued,
    ): StoredDownloadTask =
        StoredDownloadTask(
            taskId = "task-1",
            pkg = OtaPackage(
                versionName = "test",
                type = "full",
                sizeBytes = 3L,
                sourceHost = "127.0.0.1",
                downloadUrl = "http://127.0.0.1/pkg.zip",
                md5 = null,
            ),
            tempFilePath = tempFilePath,
            finalFilePath = null,
            etag = null,
            lastModified = null,
            acceptRanges = false,
            state = state,
            updatedAtMs = 100L,
        )

    private class RecordingDownloadTaskStore(
        private val observedTasks: List<StoredDownloadTask> = emptyList(),
    ) : DownloadTaskStore {
        val created = mutableListOf<String>()
        val updatedStates = mutableListOf<DownloadState>()

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
        ) {
            updatedStates += state
        }

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

        override suspend fun deleteTask(taskId: String) = Unit

        override fun observeTasks(): Flow<List<StoredDownloadTask>> = flowOf(observedTasks)
    }
}
