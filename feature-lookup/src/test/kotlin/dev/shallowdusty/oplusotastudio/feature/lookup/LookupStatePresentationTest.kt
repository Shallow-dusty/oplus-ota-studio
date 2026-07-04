package dev.shallowdusty.oplusotastudio.feature.lookup

import dev.shallowdusty.oplusotastudio.core.model.OtaErrorCategory
import dev.shallowdusty.oplusotastudio.core.model.OtaEvidenceLevel
import dev.shallowdusty.oplusotastudio.core.model.OtaPackage
import dev.shallowdusty.oplusotastudio.core.model.OtaProfile
import dev.shallowdusty.oplusotastudio.core.model.OtaRegion
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class LookupStatePresentationTest {

    @Test
    fun `detecting and querying show progress labels`() {
        val detecting = LookupUiState.Detecting.toPresentation()
        val querying = LookupUiState.Querying.toPresentation()

        assertEquals(R.string.lookup_detecting, detecting.label.resId)
        assertTrue(detecting.showProgress)

        assertEquals(R.string.lookup_querying, querying.label.resId)
        assertTrue(querying.showProgress)
    }

    @Test
    fun `ready disables lookup and shows incomplete detail when profile is incomplete`() {
        val presentation = LookupUiState.Ready(
            profile = OtaProfile(model = "", region = OtaRegion.Global, otaVersion = ""),
            device = null,
        ).toPresentation()

        assertEquals(R.string.lookup_profile_title, presentation.label.resId)
        assertFalse(presentation.canLookup)
        assertEquals(
            listOf(LookupStateText(R.string.lookup_detection_incomplete)),
            presentation.details,
        )
    }

    @Test
    fun `ready enables lookup when profile is complete`() {
        val presentation = LookupUiState.Ready(
            profile = OtaProfile(model = "LE2123", region = OtaRegion.Global, otaVersion = "11.0.2.2.LE28AA"),
            device = null,
        ).toPresentation()

        assertTrue(presentation.canLookup)
        assertTrue(presentation.details.isEmpty())
    }

    @Test
    fun `privacy disclosure exposes consent copy and actions`() {
        val presentation = LookupUiState.PrivacyDisclosureRequired(
            profile = OtaProfile(model = "LE2123", region = OtaRegion.Global, otaVersion = "11.0.2.2.LE28AA"),
            device = null,
        ).toPresentation()

        assertEquals(R.string.lookup_before_title, presentation.label.resId)
        assertEquals(
            listOf(
                LookupStateText(R.string.lookup_privacy_body),
                LookupStateText(R.string.lookup_privacy_device_id),
                LookupStateText(R.string.lookup_privacy_no_serial),
            ),
            presentation.details,
        )
        assertTrue(presentation.canContinuePrivacy)
        assertTrue(presentation.canReset)
    }

    @Test
    fun `package found exposes download action and experimental disclosure`() {
        val presentation = LookupUiState.PackageFound(
            pkg = samplePackage().copy(evidenceLevel = OtaEvidenceLevel.Synthetic),
        ).toPresentation()

        assertEquals(R.string.lookup_update_available, presentation.label.resId)
        assertTrue(presentation.canDownload)
        assertTrue(presentation.canReset)
        assertTrue(presentation.showExperimentalDisclosure)
        assertEquals(
            listOf(
                LookupStateText(R.string.lookup_experimental_notice),
                LookupStateText(R.string.lookup_verification_scope),
            ),
            presentation.details,
        )
    }

    @Test
    fun `live verified package found omits experimental disclosure`() {
        val presentation = LookupUiState.PackageFound(
            pkg = samplePackage().copy(evidenceLevel = OtaEvidenceLevel.LiveVerified),
        ).toPresentation()

        assertFalse(presentation.showExperimentalDisclosure)
        assertEquals(
            listOf(LookupStateText(R.string.lookup_verification_scope)),
            presentation.details,
        )
    }

    @Test
    fun `no update exposes reset action and no update copy`() {
        val presentation = LookupUiState.NoUpdate.toPresentation()

        assertEquals(R.string.lookup_up_to_date, presentation.label.resId)
        assertEquals(listOf(LookupStateText(R.string.lookup_no_update)), presentation.details)
        assertTrue(presentation.canReset)
    }

    @Test
    fun `errors map categories to user-facing copy and raw details`() {
        assertEquals(
            R.string.lookup_error_network,
            LookupUiState.Error(OtaErrorCategory.Network, "timeout").toPresentation().details.single().resId,
        )
        assertEquals(
            R.string.lookup_error_server,
            LookupUiState.Error(OtaErrorCategory.Server, "HTTP 503").toPresentation().details.single().resId,
        )
        assertEquals(
            R.string.lookup_error_malformed,
            LookupUiState.Error(OtaErrorCategory.Malformed, "missing url").toPresentation().details.single().resId,
        )

        val presentation = LookupUiState.Error(OtaErrorCategory.Server, "HTTP 503").toPresentation()
        assertEquals(R.string.lookup_failed, presentation.label.resId)
        assertEquals("HTTP 503", presentation.rawDetails)
        assertTrue(presentation.canReset)
    }

    private fun samplePackage() = OtaPackage(
        versionName = "12.0.0.0.LE28AA",
        type = "full",
        sizeBytes = 3_500_000_000L,
        sourceHost = "otagm.oppo.com",
        downloadUrl = "https://otagm.oppo.com/pkg.zip",
        md5 = "abc",
        sha256 = null,
    )
}
