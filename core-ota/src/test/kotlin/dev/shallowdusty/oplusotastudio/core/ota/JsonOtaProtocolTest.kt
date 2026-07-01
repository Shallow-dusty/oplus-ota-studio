package dev.shallowdusty.oplusotastudio.core.ota

import dev.shallowdusty.oplusotastudio.core.model.OtaErrorCategory
import dev.shallowdusty.oplusotastudio.core.model.OtaLookupResult
import dev.shallowdusty.oplusotastudio.core.model.OtaProfile
import dev.shallowdusty.oplusotastudio.core.model.OtaRegion
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class JsonOtaProtocolTest {

    @Test
    fun `json protocol is disabled until real evidence exists`() {
        assertFalse(JsonOtaProtocol().isEnabled)
    }

    @Test
    fun `builds json request skeleton from profile`() {
        val profile = OtaProfile(
            model = "LE2120",
            region = OtaRegion.China,
            otaVersion = "LE2120_14.0.0.1901(CN01)",
            systemType = "Color OS",
        )

        val request = JsonOtaProtocol().buildRequest(profile)

        assertEquals("otacn.oppo.com", request.host)
        assertEquals("/ota/json/disabled-unverified", request.path)
        assertEquals("application/json", request.contentType)
        assertTrue(request.body.contains(""""model":"LE2120""""))
        assertTrue(request.body.contains(""""region":"CN""""))
        assertTrue(request.body.contains(""""otaVersion":"LE2120_14.0.0.1901(CN01)""""))
        assertTrue(request.body.contains(""""systemType":"Color OS""""))
    }

    @Test
    fun `parse returns explicit disabled server error`() {
        val result = JsonOtaProtocol().parseResponse(
            rawJson = """{"data":{"url":"https://example.invalid/pkg.zip"}}""",
            sourceHost = "otacn.oppo.com",
        )

        val error = assertInstanceOf(OtaLookupResult.Error::class.java, result)
        assertEquals(OtaErrorCategory.Server, error.category)
        assertEquals("json-protocol-disabled: otacn.oppo.com", error.raw)
    }

    @Test
    fun `parses decrypted coloros component payload`() {
        val result = JsonOtaProtocol().parseDecryptedComponentPayload(
            rawJson = fixture("synthetic-coloros-oneplus9pro-cn-components.json"),
            sourceHost = "component-otapc-cn.allawntech.com",
        )

        val pkg = assertInstanceOf(OtaLookupResult.PackageFound::class.java, result).pkg
        assertEquals("LE2120_14.0.0.720(CN01)", pkg.versionName)
        assertEquals("full", pkg.type)
        assertEquals(6_559_817_109L, pkg.sizeBytes)
        assertEquals("component-otapc-cn.allawntech.com", pkg.sourceHost)
        assertEquals(
            "https://gauss-compotacostauto-cn.allawnfs.com/redacted/component-ota/24/10/16/362c202b68dc4953bb968750e196c50e.zip",
            pkg.downloadUrl,
        )
        assertEquals("362c202b68dc4953bb968750e196c50e", pkg.md5)
        assertEquals("OnePlus 9 Pro ColorOS H.21", pkg.releaseNotes)
    }

    @Test
    fun `empty coloros component payload is no update`() {
        val result = JsonOtaProtocol().parseDecryptedComponentPayload(
            rawJson = """{"versionName":"LE2120_14.0.0.720(CN01)","components":[]}""",
            sourceHost = "component-otapc-cn.allawntech.com",
        )

        assertEquals(OtaLookupResult.NoUpdate, result)
    }

    @Test
    fun `malformed coloros component payload returns malformed error`() {
        val result = JsonOtaProtocol().parseDecryptedComponentPayload(
            rawJson = """{"components":[{"componentPackets":{"size":"10","md5":"abc"}}]}""",
            sourceHost = "component-otapc-cn.allawntech.com",
        )

        val error = assertInstanceOf(OtaLookupResult.Error::class.java, result)
        assertEquals(OtaErrorCategory.Malformed, error.category)
    }

    private fun fixture(name: String): String =
        requireNotNull(javaClass.getResource("/fixtures/$name")) {
            "Missing OTA fixture: $name"
        }.readText()
}
