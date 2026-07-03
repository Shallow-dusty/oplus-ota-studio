package dev.shallowdusty.oplusotastudio.core.ota

import dev.shallowdusty.oplusotastudio.core.model.OtaEvidenceLevel
import dev.shallowdusty.oplusotastudio.core.model.OtaLookupResult
import dev.shallowdusty.oplusotastudio.core.model.OtaProfile
import dev.shallowdusty.oplusotastudio.core.model.OtaRegion
import java.io.IOException
import java.util.Base64
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import org.json.JSONObject
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Test

class ColorOsOtaLookupServiceTest {

    @Test
    fun `returns replayed package for successful ColorOS v3 response`() = runTest {
        val protocol = ColorOsOtaProtocol(
            aesKeyProvider = { ByteArray(32) { (it + 1).toByte() } },
            ivProvider = { ByteArray(16) { (it + 17).toByte() } },
            nowMs = { 123456789L },
        )
        val transport = FakeColorOsOtaTransport(responder = { request: ColorOsOtaRequest ->
            OtaHttpResponse(
                statusCode = 200,
                body = encryptedResponse(
                    request = request,
                    decryptedPayload = fixture("synthetic-coloros-oneplus9pro-cn-components.json"),
                ),
                sourceHost = request.host,
            )
        })

        val result = ColorOsOtaLookupService(
            protocol = protocol,
            transport = transport,
            evidenceLevel = OtaEvidenceLevel.ReplayedRealProfile,
        ).lookup(sampleProfile())

        val pkg = assertInstanceOf(OtaLookupResult.PackageFound::class.java, result).pkg
        assertEquals("LE2120_14.0.0.720(CN01)", pkg.versionName)
        assertEquals("component-otapc-cn.allawntech.com", transport.lastRequest?.host)
        assertEquals(OtaEvidenceLevel.ReplayedRealProfile, pkg.evidenceLevel)
    }

    @Test
    fun `maps ColorOS transport failure to network error`() = runTest {
        val result = ColorOsOtaLookupService(
            transport = FakeColorOsOtaTransport(IOException("dns failed")),
        ).lookup(sampleProfile())

        val error = assertInstanceOf(OtaLookupResult.Error::class.java, result)
        assertEquals(dev.shallowdusty.oplusotastudio.core.model.OtaErrorCategory.Network, error.category)
        assertEquals("dns failed", error.raw)
    }

    @Test
    fun `rethrows ColorOS lookup cancellation`() = runTest {
        val thrown = runCatching {
            ColorOsOtaLookupService(
                transport = FakeColorOsOtaTransport(responder = {
                    throw CancellationException("lookup canceled")
                }),
            ).lookup(sampleProfile())
        }.exceptionOrNull()

        assertInstanceOf(CancellationException::class.java, thrown)
        assertEquals("lookup canceled", thrown?.message)
    }

    private fun encryptedResponse(
        request: ColorOsOtaRequest,
        decryptedPayload: String,
    ): String {
        val iv = ByteArray(16) { (it + 33).toByte() }
        val encrypted = ColorOsCrypto().encryptCtrV2(
            plainText = decryptedPayload,
            key = Base64.getDecoder().decode(request.responseKey),
            iv = iv,
        )
        val body = JSONObject(
            mapOf(
                "cipher" to encrypted,
                "iv" to Base64.getEncoder().encodeToString(iv),
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
        )

    private fun fixture(name: String): String =
        requireNotNull(javaClass.getResource("/fixtures/$name")) {
            "Missing OTA fixture: $name"
        }.readText()

    private class FakeColorOsOtaTransport(
        private val responder: ((ColorOsOtaRequest) -> OtaHttpResponse)?,
        private val failure: IOException? = null,
    ) : ColorOsOtaTransport {
        constructor(failure: IOException) : this(null, failure)

        var lastRequest: ColorOsOtaRequest? = null

        override suspend fun post(request: ColorOsOtaRequest): OtaHttpResponse {
            lastRequest = request
            failure?.let { throw it }
            return requireNotNull(responder).invoke(request)
        }
    }
}
