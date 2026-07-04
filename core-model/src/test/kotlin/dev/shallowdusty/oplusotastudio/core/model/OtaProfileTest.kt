package dev.shallowdusty.oplusotastudio.core.model

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
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
        assertEquals(null, profile.nvCarrier)
        assertEquals(null, profile.deviceId)
        assertEquals(null, profile.language)
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

    @Test
    fun `profile can carry advanced host override`() {
        val profile = OtaProfile(
            model = "LE2123",
            region = OtaRegion.Global,
            otaVersion = "11.0.2.2.LE28AA",
            hostOverride = "ota.example.invalid",
        )

        assertEquals("ota.example.invalid", profile.hostOverride)
    }

    @Test
    fun `profile can carry live ColorOS request hints`() {
        val profile = OtaProfile(
            model = "LE2120",
            region = OtaRegion.China,
            otaVersion = "LE2120_11.H.23_0001_000000000001",
            nvCarrier = "10010111",
            deviceId = "test-device-id",
            language = "zh-Hans-CN",
        )

        assertEquals("10010111", profile.nvCarrier)
        assertEquals("test-device-id", profile.deviceId)
        assertEquals("zh-Hans-CN", profile.language)
    }

    @Test
    fun `validation accepts complete lookup profile`() {
        val profile = OtaProfile(
            model = "LE2123",
            region = OtaRegion.Global,
            otaVersion = "11.0.2.2.LE28AA",
        )

        assertEquals(emptySet<OtaProfileValidationError>(), profile.validationErrors())
        assertTrue(profile.isLookupReady)
    }

    @Test
    fun `validation reports missing required lookup fields`() {
        val profile = OtaProfile(
            model = " ",
            region = OtaRegion.Global,
            otaVersion = "",
        )

        assertEquals(
            setOf(
                OtaProfileValidationError.MissingModel,
                OtaProfileValidationError.MissingOtaVersion,
            ),
            profile.validationErrors(),
        )
        assertFalse(profile.isLookupReady)
    }

    @Test
    fun `validation accepts hostname override`() {
        val profile = OtaProfile(
            model = "LE2123",
            region = OtaRegion.Global,
            otaVersion = "11.0.2.2.LE28AA",
            hostOverride = "ota-override.example.invalid",
        )

        assertEquals(emptySet<OtaProfileValidationError>(), profile.validationErrors())
        assertTrue(profile.isLookupReady)
    }

    @Test
    fun `validation rejects malformed host override`() {
        val profiles = listOf(
            OtaProfile(
                model = "LE2123",
                region = OtaRegion.Global,
                otaVersion = "11.0.2.2.LE28AA",
                hostOverride = "https://ota.example.invalid/path",
            ),
            OtaProfile(
                model = "LE2123",
                region = OtaRegion.Global,
                otaVersion = "11.0.2.2.LE28AA",
                hostOverride = "ota host.example.invalid",
            ),
        )

        profiles.forEach { profile ->
            assertEquals(setOf(OtaProfileValidationError.InvalidHostOverride), profile.validationErrors())
            assertFalse(profile.isLookupReady)
        }
    }
}
