package dev.shallowdusty.oplusotastudio

import android.app.Application
import dev.shallowdusty.oplusotastudio.core.storage.OtaStudioDatabase
import dev.shallowdusty.oplusotastudio.core.storage.RoomPackageRepository
import dev.shallowdusty.oplusotastudio.core.storage.createOtaStudioDatabase

class OtaStudioApplication : Application() {
    private val database: OtaStudioDatabase by lazy {
        createOtaStudioDatabase(this)
    }

    val graph: AppGraph by lazy {
        AppGraph(
            downloadTempRoot = externalCacheDir ?: cacheDir,
            packageRepository = RoomPackageRepository(database.historyDao()),
        )
    }
}
