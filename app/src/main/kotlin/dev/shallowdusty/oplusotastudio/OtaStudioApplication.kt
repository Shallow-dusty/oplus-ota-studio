package dev.shallowdusty.oplusotastudio

import android.app.Application
import android.content.pm.ApplicationInfo
import android.os.Environment
import android.provider.Settings
import androidx.work.WorkManager
import dev.shallowdusty.oplusotastudio.core.download.DownloadTempFileJanitor
import dev.shallowdusty.oplusotastudio.core.download.DownloadAdmissionGate
import dev.shallowdusty.oplusotastudio.core.download.MutableDownloadAdmissionGate
import dev.shallowdusty.oplusotastudio.core.storage.OtaStudioDatabase
import dev.shallowdusty.oplusotastudio.core.storage.RoomDownloadTaskStore
import dev.shallowdusty.oplusotastudio.core.storage.RoomPackageRepository
import dev.shallowdusty.oplusotastudio.core.storage.createDownloadPreferencesStore
import dev.shallowdusty.oplusotastudio.core.storage.createLookupPrivacyConsentStore
import dev.shallowdusty.oplusotastudio.core.storage.createOtaStudioDatabase
import dev.shallowdusty.oplusotastudio.download.AndroidBatterySnapshotProvider
import dev.shallowdusty.oplusotastudio.download.BatteryDownloadAdmissionGate
import dev.shallowdusty.oplusotastudio.download.AndroidDownloadStorageSnapshotProvider
import dev.shallowdusty.oplusotastudio.download.AndroidMediaStoreDownloadFilePromoter
import dev.shallowdusty.oplusotastudio.download.DownloadWorkScheduler
import dev.shallowdusty.oplusotastudio.download.DownloadWorkerExecutor
import dev.shallowdusty.oplusotastudio.download.DownloadWorkerExecutorProvider
import dev.shallowdusty.oplusotastudio.download.WorkManagerDownloadWorkEnqueuer
import dev.shallowdusty.oplusotastudio.device.AndroidDeviceDetector
import dev.shallowdusty.oplusotastudio.logging.createAppLogArchiveExporter
import dev.shallowdusty.oplusotastudio.logging.createAppDiagnosticsProvider
import dev.shallowdusty.oplusotastudio.logging.createAppLogger
import java.io.File
import java.util.Locale
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class OtaStudioApplication : Application(), DownloadWorkerExecutorProvider {
    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val downloadAdmissionGate = MutableDownloadAdmissionGate()
    private val memoryPressureDownloadGateController = MemoryPressureDownloadGateController(
        gate = downloadAdmissionGate,
        rejectionReason = LowResourceDownloadRejectionReason,
    )

    private val database: OtaStudioDatabase by lazy {
        createOtaStudioDatabase(this)
    }

    val graph: AppGraph by lazy {
        val downloadTempRoots = downloadTempRoots()
        val downloadTempRoot = downloadTempRoots.first()
        val downloadPreferencesStore = createDownloadPreferencesStore(this)
        val batteryAdmissionGate = BatteryDownloadAdmissionGate(
            preferencesStore = downloadPreferencesStore,
            batterySnapshotProvider = AndroidBatterySnapshotProvider(this),
            scope = applicationScope,
        )
        AppGraph(
            downloadTempRoot = downloadTempRoot,
            packageRepository = RoomPackageRepository(database.historyDao()),
            downloadPreferencesStore = downloadPreferencesStore,
            lookupPrivacyConsentStore = createLookupPrivacyConsentStore(this),
            appLogger = createAppLogger(
                filesDir = filesDir,
                debuggable = isAppDebuggable(),
            ),
            appLogArchiveExporter = createAppLogArchiveExporter(filesDir = filesDir),
            appDiagnosticsProvider = createAppDiagnosticsProvider(filesDir = filesDir),
            deviceDetector = AndroidDeviceDetector(
                deviceIdProvider = {
                    Settings.Secure.getString(contentResolver, Settings.Secure.ANDROID_ID)
                },
                languageTagProvider = {
                    resources.configuration.locales.get(0)?.toLanguageTag()
                        ?: Locale.getDefault().toLanguageTag()
                },
            ),
            downloadTaskStore = RoomDownloadTaskStore(database.downloadTaskDao()),
            downloadFilePromoter = AndroidMediaStoreDownloadFilePromoter(this),
            storageSnapshotProvider = AndroidDownloadStorageSnapshotProvider(this, downloadTempRoot),
            downloadTempFileJanitor = DownloadTempFileJanitor(downloadTempRoots),
            downloadAdmissionGate = DownloadAdmissionGate {
                downloadAdmissionGate.rejectionReason()
                    ?: batteryAdmissionGate.rejectionReason()
            },
            downloadWorkScheduler = DownloadWorkScheduler(
                preferencesStore = downloadPreferencesStore,
                enqueuer = WorkManagerDownloadWorkEnqueuer(
                    WorkManager.getInstance(this),
                ),
            ),
        )
    }

    override val downloadWorkerExecutor: DownloadWorkerExecutor?
        get() = graph.downloadWorkerExecutor

    override fun onCreate() {
        super.onCreate()
        applicationScope.launch {
            graph.runStartupMaintenance()
        }
    }

    @Suppress("DEPRECATION")
    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        memoryPressureDownloadGateController.onTrimMemory(level)
    }

    private fun downloadTempRoots(): List<File> =
        listOfNotNull(
            externalCacheDir,
            getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS),
            cacheDir,
        ).distinctBy { it.absolutePath }

    private fun isAppDebuggable(): Boolean =
        (applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0

    private companion object {
        const val LowResourceDownloadRejectionReason =
            "System resource pressure is critical; new downloads are paused."
    }
}
