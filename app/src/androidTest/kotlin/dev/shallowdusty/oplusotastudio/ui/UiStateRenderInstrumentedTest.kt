package dev.shallowdusty.oplusotastudio.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.graphics.toPixelMap
import dev.shallowdusty.oplusotastudio.core.model.DeviceDetector
import dev.shallowdusty.oplusotastudio.core.model.DeviceProfile
import dev.shallowdusty.oplusotastudio.core.model.DownloadEngine
import dev.shallowdusty.oplusotastudio.core.model.DownloadState
import dev.shallowdusty.oplusotastudio.core.model.DownloadTask
import dev.shallowdusty.oplusotastudio.core.model.LookupPrivacyConsentStore
import dev.shallowdusty.oplusotastudio.core.model.OtaErrorCategory
import dev.shallowdusty.oplusotastudio.core.model.OtaEvidenceLevel
import dev.shallowdusty.oplusotastudio.core.model.OtaLookupResult
import dev.shallowdusty.oplusotastudio.core.model.OtaLookupService
import dev.shallowdusty.oplusotastudio.core.model.OtaPackage
import dev.shallowdusty.oplusotastudio.core.model.OtaProfile
import dev.shallowdusty.oplusotastudio.core.model.OtaRegion
import dev.shallowdusty.oplusotastudio.feature.downloads.DownloadsScreen
import dev.shallowdusty.oplusotastudio.feature.downloads.DownloadsViewModel
import dev.shallowdusty.oplusotastudio.feature.lookup.LookupScreen
import dev.shallowdusty.oplusotastudio.feature.lookup.LookupViewModel
import dev.shallowdusty.oplusotastudio.ui.theme.OtaStudioTheme
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class UiStateRenderInstrumentedTest {

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun lookupPackageFoundRendersExperimentalDisclosureAndCapturesNonBlankImage() {
        val pkg = samplePackage().copy(evidenceLevel = OtaEvidenceLevel.Synthetic)
        compose.setContent {
            OtaStudioTheme {
                LookupScreen(
                    factory = {
                        LookupViewModel(
                            deviceDetector = FakeDeviceDetector(readyDevice()),
                            lookupService = FakeLookupService(OtaLookupResult.PackageFound(pkg)),
                            privacyConsentStore = AcceptedConsentStore,
                        ).also { it.lookup() }
                    },
                )
            }
        }

        compose.onNodeWithText("Update available").assertIsDisplayed()
        compose.onNodeWithText("Live lookup support is experimental until this chain is live-verified.").assertIsDisplayed()
        compose.onNodeWithText("This app verifies download transfer integrity when hashes are available. It does not verify OPlus package signatures.").assertIsDisplayed()
        compose.assertRootCapturesNonBlankImage()
    }

    @Test
    fun lookupErrorRendersCategoryAndRawDetails() {
        compose.setContent {
            OtaStudioTheme {
                LookupScreen(
                    factory = {
                        LookupViewModel(
                            deviceDetector = FakeDeviceDetector(readyDevice()),
                            lookupService = FakeLookupService(OtaLookupResult.Error(OtaErrorCategory.Server, "HTTP 503")),
                            privacyConsentStore = AcceptedConsentStore,
                        ).also { it.lookup() }
                    },
                )
            }
        }

        compose.onNodeWithText("Lookup failed").assertIsDisplayed()
        compose.onNodeWithText("Server problem: the OTA service returned an error.").assertIsDisplayed()
        compose.onNodeWithText("HTTP 503").assertIsDisplayed()
        compose.assertRootCapturesNonBlankImage()
    }

    @Test
    fun downloadsScreenRendersAllCoreStateLabelsAndCapturesNonBlankImage() {
        val engine = FakeDownloadEngine(
            listOf(
                FakeDownloadTask("queued", DownloadState.Queued),
                FakeDownloadTask("running", DownloadState.Running(1_000_000L, 3_500_000L, 5_000_000L)),
                FakeDownloadTask("paused", DownloadState.Paused(DownloadState.Paused.PauseReason.NetworkLost)),
                FakeDownloadTask("retrying", DownloadState.Retrying(2, 3, OtaErrorCategory.Network)),
                FakeDownloadTask("verifying", DownloadState.Verifying),
                FakeDownloadTask("verified", DownloadState.Verified),
                FakeDownloadTask("unverified", DownloadState.Unverified),
                FakeDownloadTask("canceled", DownloadState.Canceled),
                FakeDownloadTask(
                    "failed",
                    DownloadState.Failed(
                        category = OtaErrorCategory.ChecksumMismatch,
                        retriesRemaining = 0,
                        raw = "checksum mismatch",
                        expectedHash = "abc",
                        actualHash = "def",
                    ),
                ),
            ),
        )
        compose.setContent {
            OtaStudioTheme {
                DownloadsScreen(factory = { DownloadsViewModel(engine) })
            }
        }

        compose.onNodeWithText("Queued").assertIsDisplayed()
        compose.onNodeWithText("1.0 MB / 3.5 MB").assertIsDisplayed()
        compose.onNodeWithText("Paused (networklost)").assertIsDisplayed()
        compose.onNodeWithText("Retrying (2/3) - network").assertIsDisplayed()
        compose.onNodeWithText("Verifying checksum...").assertIsDisplayed()
        compose.onNodeWithText("Transfer verified").assertIsDisplayed()
        compose.onNodeWithText("Downloaded, not verified").assertIsDisplayed()
        compose.onNodeWithText("Canceled").assertIsDisplayed()
        compose.onNodeWithText("Failed - checksummismatch").assertIsDisplayed()
        compose.assertRootCapturesNonBlankImage()
    }

    private fun androidx.compose.ui.test.junit4.ComposeContentTestRule.assertRootCapturesNonBlankImage() {
        val image = onRoot().captureToImage()
        assertTrue(image.width > 0)
        assertTrue(image.height > 0)
        val pixelMap = image.toPixelMap()
        val colors = mutableSetOf<ULong>()
        val stepX = (image.width / 12).coerceAtLeast(1)
        val stepY = (image.height / 12).coerceAtLeast(1)
        for (x in 0 until image.width step stepX) {
            for (y in 0 until image.height step stepY) {
                colors += pixelMap[x, y].value
            }
        }
        assertTrue(colors.size > 1)
    }

    private fun readyDevice() = DeviceProfile(
        model = "LE2123",
        product = "lemonadep",
        marketingName = "OnePlus 9 Pro",
        otaVersion = "11.0.2.2.LE28AA",
        buildDisplay = "LE2123_11.0.2.2.LE28AA",
        androidVersion = "14",
        securityPatch = "2026-07-01",
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
    ) : DeviceDetector {
        override suspend fun detect(): DeviceProfile = profile
    }

    private class FakeLookupService(
        private val result: OtaLookupResult,
    ) : OtaLookupService {
        override suspend fun lookup(profile: OtaProfile): OtaLookupResult = result
    }

    private object AcceptedConsentStore : LookupPrivacyConsentStore {
        override val accepted: Flow<Boolean> = flowOf(true)

        override suspend fun accept() = Unit
    }

    private class FakeDownloadEngine(
        tasks: List<DownloadTask>,
    ) : DownloadEngine {
        private val tasks = MutableStateFlow(tasks)

        override suspend fun enqueue(pkg: OtaPackage): DownloadTask = tasks.value.first()

        override fun observeAll(): Flow<List<DownloadTask>> = tasks
    }

    private class FakeDownloadTask(
        override val taskId: String,
        state: DownloadState,
    ) : DownloadTask {
        override val state: Flow<DownloadState> = flowOf(state)

        override suspend fun pause() = Unit

        override suspend fun resume() = Unit

        override suspend fun cancel() = Unit
    }
}
