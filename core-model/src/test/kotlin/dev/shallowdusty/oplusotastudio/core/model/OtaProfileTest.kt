package dev.shallowdusty.oplusotastudio.core.model

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class OtaProfileTest {

    @Test
    fun `profile requires model, region, and otaVersion`() {
        // Spec §2.3: lookup is blocked when the profile is incomplete; the
        // required fields are non-null in the type system.
        val profile = OtaProfile(
            model = "LE2123",
            region = OtaRegion.Global,
            otaVersion = "11.0.2.2.LE28AA",
        )
        assertEquals("LE2123", profile.model)
        assertEquals(OtaRegion.Global, profile.region)
        assertEquals("11.0.2.2.LE28AA", profile.otaVersion)
        // Optional fields default to null.
        assertEquals(null, profile.systemType)
        assertEquals(null, profile.deviceCodename)
    }

    @Test
    fun `profile can carry optional system type and codename`() {
        val profile = OtaProfile(
            model = "LE2123",
            region = OtaRegion.China,
            otaVersion = "11.0.2.2.LE28AA",
            systemType = "Oxygen OS",
            deviceCodename = "lemonade",
        )
        assertEquals("Oxygen OS", profile.systemType)
        assertEquals("lemonade", profile.deviceCodename)
        assertEquals(OtaRegion.China, profile.region)
    }
}
