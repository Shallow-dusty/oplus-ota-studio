package dev.shallowdusty.oplusotastudio.fake

import dev.shallowdusty.oplusotastudio.core.model.DeviceDetector
import dev.shallowdusty.oplusotastudio.core.model.DeviceProfile
import dev.shallowdusty.oplusotastudio.core.model.DownloadEngine
import dev.shallowdusty.oplusotastudio.core.model.DownloadState
import dev.shallowdusty.oplusotastudio.core.model.DownloadTask
import dev.shallowdusty.oplusotastudio.core.model.HistoryEntry
import dev.shallowdusty.oplusotastudio.core.model.OtaPackage
import dev.shallowdusty.oplusotastudio.core.model.OtaProfile
import dev.shallowdusty.oplusotastudio.core.model.OtaRegion
import dev.shallowdusty.oplusotastudio.core.model.OtaLookupResult
import dev.shallowdusty.oplusotastudio.core.model.OtaLookupService
import dev.shallowdusty.oplusotastudio.core.model.PackageRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow

// v0.0 fake implementations. These exist so the UI compiles and runs before the
// backend modules (core-ota / core-download / core-storage) land. Each is marked
// TODO and is replaced in AppGraph once the real implementation exists.

/** TODO core-ota: replace with the real DeviceDetector. */
class FakeDeviceDetector : DeviceDetector {
    override suspend fun detect(): DeviceProfile {
        delay(200) // simulate the brief "detecting…" skeleton
        return DeviceProfile(
            model = "LE2123",
            product = "OnePlus9Pro",
            marketingName = "OnePlus 9 Pro",
            otaVersion = "11.0.2.2.LE28AA",
            buildDisplay = "OnePlus9Pro_Oxygen_OS.LE28AA_11.0.2.2",
            androidVersion = "11",
            securityPatch = "2021-09-05",
            region = OtaRegion.Global,
            serialSuffix = "1234",
            incomplete = false,
        )
    }
}

/** TODO core-ota: replace with the real OtaLookupService. */
class FakeOtaLookupService : OtaLookupService {
    override suspend fun lookup(profile: OtaProfile): OtaLookupResult {
        delay(800) // simulate network round-trip
        return OtaLookupResult.PackageFound(
            OtaPackage(
                versionName = "12.0.0.0.LE28AA",
                type = "full",
                sizeBytes = 3_500_000_000L,
                sourceHost = "otagm.oppo.com",
                downloadUrl = "https://otagm.oppo.com/fake-package.zip",
                md5 = "aabbccdd11223344",
                sha256 = null,
                releaseNotes = "Fake release notes for v0.0 UI preview.",
            ),
        )
    }
}

/** TODO core-download: replace with the real DownloadEngine. */
class FakeDownloadEngine : DownloadEngine {
    private val tasks = mutableListOf<DownloadTask>()

    override suspend fun enqueue(pkg: OtaPackage): DownloadTask {
        val task = FakeDownloadTask()
        tasks.add(task)
        return task
    }

    override fun observeAll(): Flow<List<DownloadTask>> = flow { emit(tasks.toList()) }
}

private class FakeDownloadTask : DownloadTask {
    override val taskId: String = "fake-${System.nanoTime()}"
    override val state: Flow<DownloadState> = MutableStateFlow(DownloadState.Queued)
    override suspend fun pause() {}
    override suspend fun resume() {}
    override suspend fun cancel() {}
}

/** TODO core-storage: replace with the real PackageRepository. */
class FakePackageRepository : PackageRepository {
    private val history = MutableStateFlow<List<HistoryEntry>>(emptyList())

    override suspend fun record(entry: HistoryEntry) {
        history.value = history.value + entry
    }

    override fun observeHistory(): Flow<List<HistoryEntry>> = history
}
