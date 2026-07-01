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
}
