package dev.shallowdusty.oplusotastudio

import dev.shallowdusty.oplusotastudio.core.model.DeviceDetector
import dev.shallowdusty.oplusotastudio.core.model.DownloadEngine
import dev.shallowdusty.oplusotastudio.core.model.OtaLookupService
import dev.shallowdusty.oplusotastudio.core.model.PackageRepository
import dev.shallowdusty.oplusotastudio.core.download.SimpleDownloadEngine
import dev.shallowdusty.oplusotastudio.core.ota.LegacyOtaLookupService
import dev.shallowdusty.oplusotastudio.core.ota.OkHttpOtaTransport
import dev.shallowdusty.oplusotastudio.device.AndroidDeviceDetector
import dev.shallowdusty.oplusotastudio.fake.FakeDownloadEngine
import dev.shallowdusty.oplusotastudio.fake.FakePackageRepository
import java.io.File

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
) {
    val deviceDetector: DeviceDetector = AndroidDeviceDetector()
    val otaLookupService: OtaLookupService = LegacyOtaLookupService(
        transport = OkHttpOtaTransport(),
    )
    val downloadEngine: DownloadEngine = downloadTempRoot
        ?.let { SimpleDownloadEngine(tempRoot = it) }
        ?: FakeDownloadEngine()

    companion object {
        @Volatile
        private var instance: AppGraph? = null

        fun get(): AppGraph =
            instance ?: synchronized(this) {
                instance ?: AppGraph().also { instance = it }
            }
    }
}
