package dev.shallowdusty.oplusotastudio.feature.lookup

import dev.shallowdusty.oplusotastudio.core.model.DeviceDetector
import dev.shallowdusty.oplusotastudio.core.model.DeviceProfile
import dev.shallowdusty.oplusotastudio.core.model.DownloadEngine
import dev.shallowdusty.oplusotastudio.core.model.DownloadTask
import dev.shallowdusty.oplusotastudio.core.model.OtaErrorCategory
import dev.shallowdusty.oplusotastudio.core.model.OtaLookupResult
import dev.shallowdusty.oplusotastudio.core.model.OtaLookupService
import dev.shallowdusty.oplusotastudio.core.model.OtaPackage
import dev.shallowdusty.oplusotastudio.core.model.OtaProfile
import dev.shallowdusty.oplusotastudio.core.model.OtaRegion
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class LookupViewModelTest {

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @org.junit.jupiter.api.AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `detects device then becomes Ready with a profile`() = runTest {
        val vm = LookupViewModel(
            deviceDetector = FakeDeviceDetector(completeProfile()),
            lookupService = FakeLookupService(OtaLookupResult.NoUpdate),
        )
        advanceUntilIdle()
        // With UnconfinedTestDispatcher on Main, init's launch completes synchronously.
        val state = vm.uiState.value
        assertTrue(state is LookupUiState.Ready)
        val ready = state as LookupUiState.Ready
        assertEquals("LE2123", ready.profile.model)
        assertEquals("11.0.2.2.LE28AA", ready.profile.otaVersion)
        assertEquals(OtaRegion.Global, ready.profile.region)
    }

    @Test
    fun `lookup transitions Ready to Querying then PackageFound`() = runTest {
        val pkg = samplePackage()
        val vm = LookupViewModel(
            deviceDetector = FakeDeviceDetector(completeProfile()),
            lookupService = FakeLookupService(OtaLookupResult.PackageFound(pkg)),
        )
        vm.lookup()
        advanceUntilIdle()
        assertEquals(LookupUiState.PackageFound(pkg), vm.uiState.value)
    }

    @Test
    fun `lookup yields NoUpdate`() = runTest {
        val vm = LookupViewModel(
            deviceDetector = FakeDeviceDetector(completeProfile()),
            lookupService = FakeLookupService(OtaLookupResult.NoUpdate),
        )
        vm.lookup()
        advanceUntilIdle()
        assertEquals(LookupUiState.NoUpdate, vm.uiState.value)
    }

    @Test
    fun `lookup yields Error with category and raw`() = runTest {
        val vm = LookupViewModel(
            deviceDetector = FakeDeviceDetector(completeProfile()),
            lookupService = FakeLookupService(OtaLookupResult.Error(OtaErrorCategory.Server, "HTTP 503")),
        )
        vm.lookup()
        advanceUntilIdle()
        val state = vm.uiState.value
        assertTrue(state is LookupUiState.Error)
        assertEquals(OtaErrorCategory.Server, (state as LookupUiState.Error).category)
        assertEquals("HTTP 503", state.raw)
    }

    @Test
    fun `lookup is a no-op when not Ready`() = runTest {
        // Use a standard dispatcher so init's launch does NOT auto-run; the VM
        // stays in Detecting when lookup() is called, proving the guard works.
        val dispatcher = kotlinx.coroutines.test.StandardTestDispatcher()
        Dispatchers.setMain(dispatcher)
        val vm = LookupViewModel(
            deviceDetector = FakeDeviceDetector(completeProfile()),
            lookupService = FakeLookupService(OtaLookupResult.NoUpdate),
        )
        vm.lookup() // Detecting state -> guard returns early, no launch fired
        assertEquals(LookupUiState.Detecting, vm.uiState.value)
        Dispatchers.resetMain()
    }

    @Test
    fun `lookup is a no-op when otaVersion is blank`() = runTest {
        val vm = LookupViewModel(
            deviceDetector = FakeDeviceDetector(completeProfile()),
            lookupService = FakeLookupService(OtaLookupResult.NoUpdate),
        )
        vm.updateProfile(OtaProfile(model = "LE2123", region = OtaRegion.Global, otaVersion = ""))
        vm.lookup()
        advanceUntilIdle()
        // Should stay Ready — blank version blocked (spec §2.3).
        assertTrue(vm.uiState.value is LookupUiState.Ready)
    }

    @Test
    fun `incomplete detection surfaces Ready with blank profile for manual entry`() = runTest {
        val vm = LookupViewModel(
            deviceDetector = FakeDeviceDetector(
                DeviceProfile(
                    model = "LE2123",
                    product = "OnePlus9Pro",
                    marketingName = null,
                    otaVersion = null, // missing -> profile cannot be built
                    buildDisplay = "...",
                    androidVersion = "11",
                    securityPatch = null,
                    region = null,
                    serialSuffix = null,
                    incomplete = true,
                ),
            ),
            lookupService = FakeLookupService(OtaLookupResult.NoUpdate),
        )
        advanceUntilIdle()
        val state = vm.uiState.value
        assertTrue(state is LookupUiState.Ready)
        assertEquals("", (state as LookupUiState.Ready).profile.otaVersion)
        assertTrue(state.profile.model == "") // placeholder for manual entry
    }

    @Test
    fun `reset returns from PackageFound to Ready`() = runTest {
        val vm = LookupViewModel(
            deviceDetector = FakeDeviceDetector(completeProfile()),
            lookupService = FakeLookupService(OtaLookupResult.PackageFound(samplePackage())),
        )
        vm.lookup()
        advanceUntilIdle()
        assertTrue(vm.uiState.value is LookupUiState.PackageFound)
        vm.reset()
        advanceUntilIdle()
        assertTrue(vm.uiState.value is LookupUiState.Ready)
    }

    @Test
    fun `enqueueDownload forwards package to download engine`() = runTest {
        val engine = RecordingDownloadEngine()
        val pkg = samplePackage()
        val vm = LookupViewModel(
            deviceDetector = FakeDeviceDetector(completeProfile()),
            lookupService = FakeLookupService(OtaLookupResult.NoUpdate),
            downloadEngine = engine,
        )

        vm.enqueueDownload(pkg)
        advanceUntilIdle()

        assertEquals(listOf(pkg), engine.enqueued)
    }

    private fun completeProfile() = DeviceProfile(
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

    private fun samplePackage() = OtaPackage(
        versionName = "12.0.0.0.LE28AA",
        type = "full",
        sizeBytes = 3_500_000_000L,
        sourceHost = "otagm.oppo.com",
        downloadUrl = "https://otagm.oppo.com/pkg.zip",
        md5 = "abc",
        sha256 = null,
    )

    private class FakeDeviceDetector(
        private val profile: DeviceProfile,
        private val delayMs: Long = 0,
    ) : DeviceDetector {
        override suspend fun detect(): DeviceProfile {
            kotlinx.coroutines.delay(delayMs)
            return profile
        }
    }

    private class FakeLookupService(private val result: OtaLookupResult) : OtaLookupService {
        override suspend fun lookup(profile: OtaProfile): OtaLookupResult = result
    }

    private class RecordingDownloadEngine : DownloadEngine {
        val enqueued = mutableListOf<OtaPackage>()

        override suspend fun enqueue(pkg: OtaPackage): DownloadTask {
            enqueued += pkg
            return object : DownloadTask {
                override val taskId: String = "recording"
                override val state: Flow<dev.shallowdusty.oplusotastudio.core.model.DownloadState> =
                    flowOf(dev.shallowdusty.oplusotastudio.core.model.DownloadState.Queued)

                override suspend fun pause() {}
                override suspend fun resume() {}
                override suspend fun cancel() {}
            }
        }

        override fun observeAll(): Flow<List<DownloadTask>> = flowOf(emptyList())
    }
}
