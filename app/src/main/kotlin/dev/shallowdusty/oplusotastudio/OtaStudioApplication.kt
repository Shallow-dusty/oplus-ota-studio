package dev.shallowdusty.oplusotastudio

import android.app.Application
import android.os.Environment
import dev.shallowdusty.oplusotastudio.core.download.DownloadTempFileJanitor
import dev.shallowdusty.oplusotastudio.core.storage.OtaStudioDatabase
import dev.shallowdusty.oplusotastudio.core.storage.RoomDownloadTaskStore
import dev.shallowdusty.oplusotastudio.core.storage.RoomPackageRepository
import dev.shallowdusty.oplusotastudio.core.storage.createDownloadPreferencesStore
import dev.shallowdusty.oplusotastudio.core.storage.createOtaStudioDatabase
import dev.shallowdusty.oplusotastudio.download.AndroidDownloadStorageSnapshotProvider
import dev.shallowdusty.oplusotastudio.download.AndroidMediaStoreDownloadFilePromoter
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class OtaStudioApplication : Application() {
    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val database: OtaStudioDatabase by lazy {
        createOtaStudioDatabase(this)
    }

    val graph: AppGraph by lazy {
        val downloadTempRoots = downloadTempRoots()
        val downloadTempRoot = downloadTempRoots.first()
        AppGraph(
            downloadTempRoot = downloadTempRoot,
            packageRepository = RoomPackageRepository(database.historyDao()),
            downloadPreferencesStore = createDownloadPreferencesStore(this),
            downloadTaskStore = RoomDownloadTaskStore(database.downloadTaskDao()),
            downloadFilePromoter = AndroidMediaStoreDownloadFilePromoter(this),
            storageSnapshotProvider = AndroidDownloadStorageSnapshotProvider(this, downloadTempRoot),
            downloadTempFileJanitor = DownloadTempFileJanitor(downloadTempRoots),
        )
    }

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
