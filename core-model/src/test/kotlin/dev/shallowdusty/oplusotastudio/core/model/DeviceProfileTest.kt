package dev.shallowdusty.oplusotastudio.core.model

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class DeviceProfileTest {

    @Test
    fun `incomplete profile has nulls for best-effort fields`() {
        // Spec §2.3: on most Android 9+ non-root devices, hidden-API policy
        // blocks ro.* reads, so the profile is frequently incomplete.
        val profile = DeviceProfile(
            model = "LE2123",
            product = "OnePlus9Pro",
            marketingName = null,
            otaVersion = null,
            buildDisplay = "OnePlus9Pro_Oxygen_OS.LE28AA_11.0.2.2",
            androidVersion = "11",
            securityPatch = "2021-09-05",
            region = null,
            serialSuffix = null,
            incomplete = true,
        )
        assertTrue(profile.incomplete)
        assertNull(profile.otaVersion)
        assertNull(profile.marketingName)
        assertNull(profile.region)
        // buildDisplay is the reliable fallback for parsing the build string.
        assertEquals("OnePlus9Pro_Oxygen_OS.LE28AA_11.0.2.2", profile.buildDisplay)
    }

    @Test
    fun `complete profile populates all fields`() {
        val profile = DeviceProfile(
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
        assertEquals("11.0.2.2.LE28AA", profile.otaVersion)
        assertEquals(OtaRegion.Global, profile.region)
        assertEquals("1234", profile.serialSuffix)
    }
}
