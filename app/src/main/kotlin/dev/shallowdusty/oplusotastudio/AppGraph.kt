package dev.shallowdusty.oplusotastudio

import dev.shallowdusty.oplusotastudio.core.download.DownloadFilePromoter
import dev.shallowdusty.oplusotastudio.core.download.DownloadAdmissionGate
import dev.shallowdusty.oplusotastudio.core.download.DownloadStorageSnapshot
import dev.shallowdusty.oplusotastudio.core.download.DownloadTempFileJanitor
import dev.shallowdusty.oplusotastudio.core.download.DownloadTempFileJanitorResult
import dev.shallowdusty.oplusotastudio.core.download.SimpleDownloadEngine
import dev.shallowdusty.oplusotastudio.core.model.DeviceDetector
import dev.shallowdusty.oplusotastudio.core.model.DownloadEngine
import dev.shallowdusty.oplusotastudio.core.model.DownloadPreferencesStore
import dev.shallowdusty.oplusotastudio.core.model.DownloadTaskStore
import dev.shallowdusty.oplusotastudio.core.model.AlwaysAcceptedLookupPrivacyConsentStore
import dev.shallowdusty.oplusotastudio.core.model.LookupPrivacyConsentStore
import dev.shallowdusty.oplusotastudio.core.model.OtaLookupService
import dev.shallowdusty.oplusotastudio.core.model.PackageRepository
import dev.shallowdusty.oplusotastudio.core.ota.ColorOsOtaLookupService
import dev.shallowdusty.oplusotastudio.device.AndroidDeviceDetector
import dev.shallowdusty.oplusotastudio.download.DownloadTaskWorkScheduler
import dev.shallowdusty.oplusotastudio.download.DownloadWorkerExecutor
import dev.shallowdusty.oplusotastudio.download.DownloadWorkerExecutorAdapter
import dev.shallowdusty.oplusotastudio.download.SerialDownloadWorkerExecutor
import dev.shallowdusty.oplusotastudio.download.WorkScheduledDownloadEngine
import dev.shallowdusty.oplusotastudio.fake.FakeDownloadEngine
import dev.shallowdusty.oplusotastudio.fake.FakeDownloadPreferencesStore
import dev.shallowdusty.oplusotastudio.fake.FakePackageRepository
import dev.shallowdusty.oplusotastudio.logging.AppDiagnosticsProvider
import dev.shallowdusty.oplusotastudio.logging.AppLogArchiveExporter
import dev.shallowdusty.oplusotastudio.logging.AppLogLevel
import dev.shallowdusty.oplusotastudio.logging.AppLogger
import dev.shallowdusty.oplusotastudio.logging.LoggingDownloadEngine
import dev.shallowdusty.oplusotastudio.logging.LoggingOtaLookupService
import dev.shallowdusty.oplusotastudio.logging.NoOpAppLogSink
import java.io.File
import kotlinx.coroutines.flow.first

/**
 * Manual DI container (spec: no Hilt in v0.0 to avoid deciding the backend's DI
 * framework and to keep a KSP dependency out of the frontend).
 *
 * Holds the service implementations the app injects into feature ViewModels.
 * During v0.0 these are fakes; once core-ota / core-download / core-storage
 * land, swap each `Fake*` for the real implementation here — feature code and
 * ViewModels stay unchanged (dependency inversion via core-model contracts).
 */
class AppGraph(
    private val downloadTempRoot: File? = null,
    val packageRepository: PackageRepository = FakePackageRepository(),
    val downloadPreferencesStore: DownloadPreferencesStore = FakeDownloadPreferencesStore(),
    val lookupPrivacyConsentStore: LookupPrivacyConsentStore = AlwaysAcceptedLookupPrivacyConsentStore,
    val appLogger: AppLogger = AppLogger(
        sink = NoOpAppLogSink,
        minLevel = AppLogLevel.Info,
    ),
    val appLogArchiveExporter: AppLogArchiveExporter? = null,
    val appDiagnosticsProvider: AppDiagnosticsProvider? = null,
    val deviceDetector: DeviceDetector = AndroidDeviceDetector(),
    private val downloadTaskStore: DownloadTaskStore? = null,
    private val downloadFilePromoter: DownloadFilePromoter? = null,
    private val storageSnapshotProvider: (() -> DownloadStorageSnapshot)? = null,
    private val downloadTempFileJanitor: DownloadTempFileJanitor? = null,
    private val downloadAdmissionGate: DownloadAdmissionGate = DownloadAdmissionGate.AllowAll,
    val downloadWorkScheduler: DownloadTaskWorkScheduler? = null,
    downloadWorkerExecutor: DownloadWorkerExecutor? = null,
) {
    val otaLookupService: OtaLookupService = LoggingOtaLookupService(
        delegate = ColorOsOtaLookupService(),
        logger = appLogger,
    )
    private val realDownloadEngine: SimpleDownloadEngine? = downloadTempRoot
        ?.let { tempRoot ->
            SimpleDownloadEngine(
                tempRoot = tempRoot,
                taskStore = downloadTaskStore,
                packageRepository = packageRepository,
                filePromoter = downloadFilePromoter,
                storageSnapshotProvider = storageSnapshotProvider,
                admissionGate = downloadAdmissionGate,
            )
        }
    private val workScheduledDownloadEngine: WorkScheduledDownloadEngine? =
        if (downloadTempRoot != null && downloadTaskStore != null && downloadWorkScheduler != null) {
            WorkScheduledDownloadEngine(
                taskStore = downloadTaskStore,
                scheduler = downloadWorkScheduler,
                tempRoot = downloadTempRoot,
                admissionGate = downloadAdmissionGate,
            )
        } else {
            null
        }
    private val downloadEngineDelegate: DownloadEngine =
        workScheduledDownloadEngine ?: realDownloadEngine ?: FakeDownloadEngine()
    val downloadEngine: DownloadEngine = LoggingDownloadEngine(
        delegate = downloadEngineDelegate,
        logger = appLogger,
    )
    val downloadWorkerExecutor: DownloadWorkerExecutor? =
        downloadWorkerExecutor ?: realDownloadEngine?.let { engine ->
            SerialDownloadWorkerExecutor(
                DownloadWorkerExecutorAdapter(
                    stopStoredTask = engine::stopStoredTask,
                    executeStoredTask = engine::executeStoredTask,
                ),
            )
        }

    suspend fun cleanOrphanedDownloadParts(): DownloadTempFileJanitorResult? {
        val store = downloadTaskStore ?: return null
        val janitor = downloadTempFileJanitor ?: return null
        val activeTempFilePaths = store.observeTasks()
            .first()
            .mapTo(mutableSetOf()) { it.tempFilePath }
        return janitor.deleteOrphanedParts(activeTempFilePaths)
    }

    suspend fun runStartupMaintenance() {
        cleanOrphanedDownloadParts()
        workScheduledDownloadEngine?.rescheduleRecoverableTasks()
    }

    companion object {
        @Volatile
        private var instance: AppGraph? = null

        fun get(): AppGraph =
            instance ?: synchronized(this) {
                instance ?: AppGraph().also { instance = it }
            }
    }
}
