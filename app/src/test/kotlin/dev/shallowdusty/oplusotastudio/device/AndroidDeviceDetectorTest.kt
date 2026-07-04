package dev.shallowdusty.oplusotastudio.device

import dev.shallowdusty.oplusotastudio.core.model.OtaRegion
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class AndroidDeviceDetectorTest {

    @Test
    fun `builds device profile from build facts and best effort properties`() = runTest {
        val detector = AndroidDeviceDetector(
            buildFactsProvider = {
                AndroidBuildFacts(
                    model = "LE2120",
                    product = "OnePlus9Pro_CH",
                    display = "LE2120_11.H.23_0001_000000000001",
                    androidVersion = "14",
                    securityPatch = "2026-06-05",
                )
            },
            propertyProvider = MapDevicePropertyProvider(
                "ro.oppo.market.name" to "OnePlus 9 Pro",
                "ro.build.version.ota" to "LE2120_11.H.23_0001_000000000001",
                "ro.oppo.region" to "CN",
                "ro.build.oplus_nv_id" to "10010111",
            ),
            deviceIdProvider = { "test-android-id" },
            languageTagProvider = { "zh-Hans-CN" },
            localeCountryProvider = { "US" },
        )

        val profile = detector.detect()

        assertEquals("LE2120", profile.model)
        assertEquals("OnePlus9Pro_CH", profile.product)
        assertEquals("OnePlus 9 Pro", profile.marketingName)
        assertEquals("LE2120_11.H.23_0001_000000000001", profile.otaVersion)
        assertEquals("14", profile.androidVersion)
        assertEquals("2026-06-05", profile.securityPatch)
        assertEquals(OtaRegion.China, profile.region)
        assertEquals("10010111", profile.nvCarrier)
        assertEquals("test-android-id", profile.deviceId)
        assertEquals("zh-Hans-CN", profile.language)
        assertFalse(profile.incomplete)
    }

    @Test
    fun `marks profile incomplete when OTA version cannot be resolved`() = runTest {
        val detector = AndroidDeviceDetector(
            buildFactsProvider = {
                AndroidBuildFacts(
                    model = "LE2120",
                    product = "OnePlus9Pro_CH",
                    display = "unknown",
                    androidVersion = "14",
                    securityPatch = null,
                )
            },
            propertyProvider = MapDevicePropertyProvider(),
            localeCountryProvider = { "US" },
        )

        val profile = detector.detect()

        assertEquals(null, profile.otaVersion)
        assertEquals(OtaRegion.Global, profile.region)
        assertTrue(profile.incomplete)
    }

    @Test
    fun `recovers OTA version from display build string`() = runTest {
        val detector = AndroidDeviceDetector(
            buildFactsProvider = {
                AndroidBuildFacts(
                    model = "LE2123",
                    product = "OnePlus9Pro",
                    display = "OnePlus9Pro_Oxygen_OS.LE28AA_11.0.2.2",
                    androidVersion = "11",
                    securityPatch = "2021-09-05",
                )
            },
            propertyProvider = MapDevicePropertyProvider(),
            localeCountryProvider = { "US" },
        )

        val profile = detector.detect()

        assertEquals("11.0.2.2.LE28AA", profile.otaVersion)
        assertFalse(profile.incomplete)
    }

    @Test
    fun `uses OPlus region property before locale fallback`() = runTest {
        val detector = AndroidDeviceDetector(
            buildFactsProvider = {
                AndroidBuildFacts(
                    model = "LE2120",
                    product = "OnePlus9Pro_CH",
                    display = "LE2120_14.0.0.1901(CN01)",
                    androidVersion = "14",
                    securityPatch = null,
                )
            },
            propertyProvider = MapDevicePropertyProvider(
                "persist.sys.oplus.region" to "CN",
            ),
            localeCountryProvider = { "US" },
        )

        val profile = detector.detect()

        assertEquals(OtaRegion.China, profile.region)
    }

    @Test
    fun `preserves ColorOS display version with region suffix`() = runTest {
        val detector = AndroidDeviceDetector(
            buildFactsProvider = {
                AndroidBuildFacts(
                    model = "LE2120",
                    product = "OnePlus9Pro_CH",
                    display = "LE2120_14.0.0.1901(CN01)",
                    androidVersion = "14",
                    securityPatch = null,
                )
            },
            propertyProvider = MapDevicePropertyProvider(),
            localeCountryProvider = { "CN" },
        )

        val profile = detector.detect()

        assertEquals("LE2120_14.0.0.1901(CN01)", profile.otaVersion)
        assertFalse(profile.incomplete)
    }
}
