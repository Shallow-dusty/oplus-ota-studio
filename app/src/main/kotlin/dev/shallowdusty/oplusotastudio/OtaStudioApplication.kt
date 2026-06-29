package dev.shallowdusty.oplusotastudio

import android.app.Application
import android.os.Environment
import dev.shallowdusty.oplusotastudio.core.storage.OtaStudioDatabase
import dev.shallowdusty.oplusotastudio.core.storage.RoomDownloadTaskStore
import dev.shallowdusty.oplusotastudio.core.storage.RoomPackageRepository
import dev.shallowdusty.oplusotastudio.core.storage.createOtaStudioDatabase
import dev.shallowdusty.oplusotastudio.download.AndroidDownloadStorageSnapshotProvider
import dev.shallowdusty.oplusotastudio.download.AndroidMediaStoreDownloadFilePromoter

class OtaStudioApplication : Application() {
    private val database: OtaStudioDatabase by lazy {
        createOtaStudioDatabase(this)
    }

    val graph: AppGraph by lazy {
        val downloadTempRoot = externalCacheDir
            ?: getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
            ?: cacheDir
        AppGraph(
            downloadTempRoot = downloadTempRoot,
            packageRepository = RoomPackageRepository(database.historyDao()),
            downloadTaskStore = RoomDownloadTaskStore(database.downloadTaskDao()),
            downloadFilePromoter = AndroidMediaStoreDownloadFilePromoter(this),
            storageSnapshotProvider = AndroidDownloadStorageSnapshotProvider(this, downloadTempRoot),
        )
    }
}
