package dev.shallowdusty.oplusotastudio.core.ota

import dev.shallowdusty.oplusotastudio.core.model.OtaEvidenceLevel
import dev.shallowdusty.oplusotastudio.core.model.OtaLookupResult
import dev.shallowdusty.oplusotastudio.core.model.OtaProfile
import dev.shallowdusty.oplusotastudio.core.model.OtaRegion
import java.util.Base64
import org.json.JSONObject
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ColorOsOtaProtocolTest {

    private val aesKey = ByteArray(32) { (it + 1).toByte() }
    private val requestIv = ByteArray(16) { (it + 17).toByte() }
    private val responseIv = ByteArray(16) { (it + 33).toByte() }

    @Test
    fun `builds decryptable ColorOS v3 request for China OnePlus profile`() {
        val protocol = protocol()

        val request = protocol.buildRequest(sampleProfile())

        assertEquals("https://component-otapc-cn.allawntech.com/update/v3", request.url)
        assertEquals("component-otapc-cn.allawntech.com", request.host)
        assertEquals("application/json", request.contentType)
        assertEquals("2", request.headers["version"])
        assertEquals("zh-Hans-CN", request.headers["language"])
        assertEquals("OnePlus9Pro_CH", request.headers["model"])
        assertEquals("LE2120_11.H.23_0001_000000000001", request.headers["otaVersion"])
        assertEquals("LE2120_11.H.23", request.headers["romVersion"])
        assertEquals("10010111", request.headers["nvCarrier"])
        assertEquals(
            "7C810AC9D1BED620E66595F9554920EC4590220D405C5A4C2E9E21CCBC858349",
            request.headers["deviceId"],
        )
        assertTrue(request.headers.getValue("protectedKey").contains("SCENE_1"))

        val params = JSONObject(JSONObject(request.body).getString("params"))
        val decrypted = ColorOsCrypto().decryptCtrV2(
            cipher = params.getString("cipher"),
            key = request.responseKey,
            iv = params.getString("iv"),
        )
        val plain = JSONObject(decrypted)
        assertEquals("OnePlus9Pro_CH", plain.getString("model"))
        assertEquals("OnePlus9Pro_CH", plain.getString("productName"))
        assertEquals("LE2120_11.H.23_0001_000000000001", plain.getString("otaVersion"))
        assertEquals("LE2120_11.H.23", plain.getString("otaPrefix"))
        assertEquals("CN", plain.getString("uRegion"))
        assertEquals("CN", plain.getString("trackRegion"))
        assertEquals("Android14.0", plain.getString("androidVersion"))
        assertEquals("ColorOS14", plain.getString("colorOSVersion"))
        assertEquals("zh-Hans-CN", plain.getString("language"))
        assertEquals(
            "7C810AC9D1BED620E66595F9554920EC4590220D405C5A4C2E9E21CCBC858349",
            plain.getString("deviceId"),
        )
    }

    @Test
    fun `decrypts ColorOS v3 component response into replayed package`() {
        val protocol = protocol()
        val request = protocol.buildRequest(sampleProfile())
        val encryptedResponse = encryptedResponse(
            request = request,
            decryptedPayload = fixture("synthetic-coloros-oneplus9pro-cn-components.json"),
        )

        val result = protocol.parseResponse(
            request = request,
            rawJson = encryptedResponse,
            evidenceLevel = OtaEvidenceLevel.ReplayedRealProfile,
        )

        val pkg = assertInstanceOf(OtaLookupResult.PackageFound::class.java, result).pkg
        assertEquals("LE2120_14.0.0.720(CN01)", pkg.versionName)
        assertEquals("component-otapc-cn.allawntech.com", pkg.sourceHost)
        assertEquals("362c202b68dc4953bb968750e196c50e", pkg.md5)
        assertEquals(OtaEvidenceLevel.ReplayedRealProfile, pkg.evidenceLevel)
    }

    @Test
    fun `maps ColorOS 304 response to no update`() {
        val protocol = protocol()
        val request = protocol.buildRequest(sampleProfile())

        val result = protocol.parseResponse(
            request = request,
            rawJson = """{"responseCode":304,"errMsg":"not modified"}""",
        )

        assertEquals(OtaLookupResult.NoUpdate, result)
    }

    private fun protocol(): ColorOsOtaProtocol =
        ColorOsOtaProtocol(
            aesKeyProvider = { aesKey },
            ivProvider = { requestIv },
            nowMs = { 123456789L },
        )

    private fun encryptedResponse(
        request: ColorOsOtaRequest,
        decryptedPayload: String,
    ): String {
        val encrypted = ColorOsCrypto().encryptCtrV2(
            plainText = decryptedPayload,
            key = Base64.getDecoder().decode(request.responseKey),
            iv = responseIv,
        )
        val body = JSONObject(
            mapOf(
                "cipher" to encrypted,
                "iv" to Base64.getEncoder().encodeToString(responseIv),
            ),
        )
        return JSONObject(
            mapOf(
                "responseCode" to 200,
                "body" to body.toString(),
            ),
        ).toString()
    }

    private fun sampleProfile(): OtaProfile =
        OtaProfile(
            model = "LE2120",
            region = OtaRegion.China,
            otaVersion = "LE2120_11.H.23_0001_000000000001",
            systemType = "Color OS",
            deviceCodename = "OnePlus9Pro_CH",
            nvCarrier = "10010111",
            deviceId = "test-device-id",
            language = "zh-Hans-CN",
        )

    private fun fixture(name: String): String =
        requireNotNull(javaClass.getResource("/fixtures/$name")) {
            "Missing OTA fixture: $name"
        }.readText()
}
