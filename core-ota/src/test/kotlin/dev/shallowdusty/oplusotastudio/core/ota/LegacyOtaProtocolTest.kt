package dev.shallowdusty.oplusotastudio.core.ota

import dev.shallowdusty.oplusotastudio.core.model.OtaLookupResult
import dev.shallowdusty.oplusotastudio.core.model.OtaProfile
import dev.shallowdusty.oplusotastudio.core.model.OtaRegion
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class LegacyOtaProtocolTest {

    @Test
    fun `builds legacy form request from profile`() {
        val profile = OtaProfile(
            model = "LE2120",
            region = OtaRegion.China,
            otaVersion = "LE2120_11.H.23_0001_000000000001",
            systemType = "Color OS",
            deviceCodename = "OnePlus9Pro_CH",
        )

        val request = LegacyOtaProtocol().buildRequest(profile)

        assertEquals("otacn.oppo.com", request.host)
        assertEquals("/OnePlusOTA/OnePlus_OTA.php", request.path)
        assertEquals("application/x-www-form-urlencoded", request.contentType)
        assertTrue(request.body.contains("systemType=Color+OS"))
        assertTrue(request.body.contains("otaVersion=LE2120_11.H.23_0001_000000000001"))
        assertTrue(request.body.contains("device=OnePlus9Pro_CH"))
    }

    @Test
    fun `builds legacy request with profile host override`() {
        val profile = OtaProfile(
            model = "LE2120",
            region = OtaRegion.China,
            otaVersion = "LE2120_11.H.23_0001_000000000001",
            hostOverride = "ota-override.example.invalid",
        )

        val request = LegacyOtaProtocol().buildRequest(profile)

        assertEquals("ota-override.example.invalid", request.host)
    }

    @Test
    fun `parses legacy package found response`() {
        val xml = fixture("synthetic-legacy-oneplus9pro-cn-success.xml")

        val result = LegacyOtaProtocol().parseResponse(
            rawXml = xml,
            sourceHost = "example.invalid",
        )

        val pkg = assertInstanceOf(OtaLookupResult.PackageFound::class.java, result).pkg
        assertEquals("LE2120_14.0.0.1901(CN01)", pkg.versionName)
        assertEquals("full", pkg.type)
        assertEquals(6_559_817_109L, pkg.sizeBytes)
        assertEquals("example.invalid", pkg.sourceHost)
        assertEquals("https://example.invalid/oneplus9pro-cn-full.zip", pkg.downloadUrl)
        assertEquals("5ae1e4d8101218d58c1da10092b22996", pkg.md5)
    }

    @Test
    fun `parses legacy no update response`() {
        val xml = fixture("synthetic-legacy-oneplus9pro-cn-noupdate.xml")

        val result = LegacyOtaProtocol().parseResponse(
            rawXml = xml,
            sourceHost = "otagm.oppo.com",
        )

        assertEquals(OtaLookupResult.NoUpdate, result)
    }

    @Test
    fun `maps malformed legacy response to malformed error`() {
        val result = LegacyOtaProtocol().parseResponse(
            rawXml = fixture("synthetic-legacy-oneplus9pro-cn-missing-url.xml"),
            sourceHost = "otagm.oppo.com",
        )

        val error = assertInstanceOf(OtaLookupResult.Error::class.java, result)
        assertEquals(dev.shallowdusty.oplusotastudio.core.model.OtaErrorCategory.Malformed, error.category)
    }

    @Test
    fun `rejects legacy package response with cleartext download url`() {
        val xml = """
            <root>
              <Command>NEW_VERSION</Command>
              <versionName>LE2120_14.0.0.1901(CN01)</versionName>
              <type>full</type>
              <size>6559817109</size>
              <md5>5ae1e4d8101218d58c1da10092b22996</md5>
              <url>http://example.invalid/oneplus9pro-cn-full.zip</url>
            </root>
        """.trimIndent()

        val result = LegacyOtaProtocol().parseResponse(
            rawXml = xml,
            sourceHost = "example.invalid",
        )

        val error = assertInstanceOf(OtaLookupResult.Error::class.java, result)
        assertEquals(dev.shallowdusty.oplusotastudio.core.model.OtaErrorCategory.Malformed, error.category)
    }

    @Test
    fun `rejects legacy response with doctype declarations`() {
        val xml = fixture("synthetic-legacy-oneplus9pro-cn-doctype.xml")

        val result = LegacyOtaProtocol().parseResponse(
            rawXml = xml,
            sourceHost = "gauss-compotacostauto-cn.allawnfs.com",
        )

        val error = assertInstanceOf(OtaLookupResult.Error::class.java, result)
        assertEquals(dev.shallowdusty.oplusotastudio.core.model.OtaErrorCategory.Malformed, error.category)
    }

    private fun fixture(name: String): String =
        requireNotNull(javaClass.getResource("/fixtures/$name")) {
            "Missing OTA fixture: $name"
        }.readText()
}
