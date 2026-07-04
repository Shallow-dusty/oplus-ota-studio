package dev.shallowdusty.oplusotastudio.core.ota

import dev.shallowdusty.oplusotastudio.core.model.OtaEvidenceLevel
import dev.shallowdusty.oplusotastudio.core.model.OtaLookupResult
import dev.shallowdusty.oplusotastudio.core.model.OtaProfile
import dev.shallowdusty.oplusotastudio.core.model.OtaRegion
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Test

class LiveColorOsOtaCurrentBuildTest {

    @Test
    fun `treats current OnePlus 9 Pro China profile empty artifact response as no update`() = runTest {
        assumeTrue(
            System.getProperty("liveOta") == "true" || System.getenv("LIVE_OTA") == "true",
            "Pass -DliveOta=true or LIVE_OTA=true to run live OTA diagnostics.",
        )

        val profile = OtaProfile(
            model = System.getProperty("model") ?: "LE2120",
            region = OtaRegion.China,
            otaVersion = System.getProperty("otaVersion") ?: "LE2120_11.H.23_3230_202504181723",
            systemType = "Color OS",
            deviceCodename = System.getProperty("deviceCodename") ?: "OnePlus9Pro_CH",
            nvCarrier = System.getProperty("nvCarrier") ?: "10010111",
            deviceId = System.getProperty("deviceId") ?: System.getenv("OTA_DEVICE_ID"),
            language = System.getProperty("language") ?: "zh-Hans-CN",
        )
        val protocol = ColorOsOtaProtocol()
        val request = protocol.buildRequest(profile)
        val response = OkHttpColorOsOtaTransport().post(request)
        val result = if (response.statusCode in 200..299) {
            protocol.parseResponse(
                request = request,
                rawJson = response.body,
                evidenceLevel = OtaEvidenceLevel.ReplayedRealProfile,
            )
        } else {
            OtaLookupResult.Error(
                category = dev.shallowdusty.oplusotastudio.core.model.OtaErrorCategory.Server,
                raw = "HTTP ${response.statusCode}: ${response.body}",
            )
        }

        val diagnostic = buildString {
            appendLine("LIVE_OTA_STATUS=${response.statusCode}")
            appendLine("LIVE_OTA_BODY=${redactUrls(response.body).take(2_000)}")
            appendLine("LIVE_OTA_RESULT=${redactUrls(result.toString()).take(2_000)}")
        }
        assertEquals(200, response.statusCode, diagnostic)
        assertEquals(
            OtaLookupResult.NoUpdate,
            result,
            diagnostic,
        )
    }

    private fun redactUrls(value: String): String =
        value.replace(Regex("""https://[^"\s]+"""), "https://<redacted>")
}
