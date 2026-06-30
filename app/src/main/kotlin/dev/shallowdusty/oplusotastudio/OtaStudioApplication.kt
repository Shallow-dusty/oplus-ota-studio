package dev.shallowdusty.oplusotastudio

import android.app.Application
import android.os.Environment
import androidx.work.WorkManager
import dev.shallowdusty.oplusotastudio.core.download.DownloadTempFileJanitor
import dev.shallowdusty.oplusotastudio.core.storage.OtaStudioDatabase
import dev.shallowdusty.oplusotastudio.core.storage.RoomDownloadTaskStore
import dev.shallowdusty.oplusotastudio.core.storage.RoomPackageRepository
import dev.shallowdusty.oplusotastudio.core.storage.createDownloadPreferencesStore
import dev.shallowdusty.oplusotastudio.core.storage.createOtaStudioDatabase
import dev.shallowdusty.oplusotastudio.download.AndroidDownloadStorageSnapshotProvider
import dev.shallowdusty.oplusotastudio.download.AndroidMediaStoreDownloadFilePromoter
import dev.shallowdusty.oplusotastudio.download.DownloadWorkScheduler
import dev.shallowdusty.oplusotastudio.download.DownloadWorkerExecutor
import dev.shallowdusty.oplusotastudio.download.DownloadWorkerExecutorProvider
import dev.shallowdusty.oplusotastudio.download.WorkManagerDownloadWorkEnqueuer
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class OtaStudioApplication : Application(), DownloadWorkerExecutorProvider {
    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val database: OtaStudioDatabase by lazy {
        createOtaStudioDatabase(this)
    }

    val graph: AppGraph by lazy {
        val downloadTempRoots = downloadTempRoots()
        val downloadTempRoot = downloadTempRoots.first()
        val downloadPreferencesStore = createDownloadPreferencesStore(this)
        AppGraph(
            downloadTempRoot = downloadTempRoot,
            packageRepository = RoomPackageRepository(database.historyDao()),
            downloadPreferencesStore = downloadPreferencesStore,
            downloadTaskStore = RoomDownloadTaskStore(database.downloadTaskDao()),
            downloadFilePromoter = AndroidMediaStoreDownloadFilePromoter(this),
            storageSnapshotProvider = AndroidDownloadStorageSnapshotProvider(this, downloadTempRoot),
            downloadTempFileJanitor = DownloadTempFileJanitor(downloadTempRoots),
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
            graph.cleanOrphanedDownloadParts()
        }
    }

    private fun downloadTempRoots(): List<File> =
        listOfNotNull(
            externalCacheDir,
            getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS),
            cacheDir,
        ).distinctBy { it.absolutePath }
}
