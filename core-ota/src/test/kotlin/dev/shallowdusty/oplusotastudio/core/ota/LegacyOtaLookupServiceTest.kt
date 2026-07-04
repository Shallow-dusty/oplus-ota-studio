package dev.shallowdusty.oplusotastudio.core.ota

import dev.shallowdusty.oplusotastudio.core.model.OtaErrorCategory
import dev.shallowdusty.oplusotastudio.core.model.OtaLookupResult
import dev.shallowdusty.oplusotastudio.core.model.OtaProfile
import dev.shallowdusty.oplusotastudio.core.model.OtaRegion
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Test

class LegacyOtaLookupServiceTest {

    @Test
    fun `returns parsed package for successful HTTP response`() = runTest {
        val transport = FakeOtaTransport(
            OtaHttpResponse(
                statusCode = 200,
                body = """
                    <root>
                      <Command>NEW_VERSION</Command>
                      <versionName>LE2120_14.0.0.1901(CN01)</versionName>
                      <type>full</type>
                      <size>6559817109</size>
                      <md5>5ae1e4d8101218d58c1da10092b22996</md5>
                      <url>https://gauss-compotacostauto-cn.allawnfs.com/package.zip</url>
                    </root>
                """.trimIndent(),
                sourceHost = "gauss-compotacostauto-cn.allawnfs.com",
            ),
        )

        val result = LegacyOtaLookupService(transport = transport).lookup(sampleProfile())

        val pkg = assertInstanceOf(OtaLookupResult.PackageFound::class.java, result).pkg
        assertEquals("LE2120_14.0.0.1901(CN01)", pkg.versionName)
        assertEquals("gauss-compotacostauto-cn.allawnfs.com", pkg.sourceHost)
        assertEquals("otacn.oppo.com", transport.lastRequest?.host)
    }

    @Test
    fun `maps non successful HTTP response to server error`() = runTest {
        val transport = FakeOtaTransport(
            OtaHttpResponse(
                statusCode = 503,
                body = "maintenance",
                sourceHost = "otacn.oppo.com",
            ),
        )

        val result = LegacyOtaLookupService(transport = transport).lookup(sampleProfile())

        val error = assertInstanceOf(OtaLookupResult.Error::class.java, result)
        assertEquals(OtaErrorCategory.Server, error.category)
        assertEquals("HTTP 503: maintenance", error.raw)
    }

    @Test
    fun `maps transport IO failure to network error`() = runTest {
        val transport = FakeOtaTransport(IOException("timeout"))

        val result = LegacyOtaLookupService(transport = transport).lookup(sampleProfile())

        val error = assertInstanceOf(OtaLookupResult.Error::class.java, result)
        assertEquals(OtaErrorCategory.Network, error.category)
        assertEquals("timeout", error.raw)
    }

    @Test
    fun `rethrows lookup cancellation`() = runTest {
        val thrown = runCatching {
            LegacyOtaLookupService(
                transport = FakeOtaTransport(
                    failure = CancellationException("lookup canceled"),
                ),
            ).lookup(sampleProfile())
        }.exceptionOrNull()

        assertInstanceOf(CancellationException::class.java, thrown)
        assertEquals("lookup canceled", thrown?.message)
    }

    @Test
    fun `rejects invalid profile before sending request`() = runTest {
        val transport = FakeOtaTransport(
            OtaHttpResponse(
                statusCode = 200,
                body = "<root><Command>NO_NEW_VERSION</Command></root>",
                sourceHost = "otacn.oppo.com",
            ),
        )

        val result = LegacyOtaLookupService(transport = transport).lookup(
            OtaProfile(
                model = "",
                region = OtaRegion.China,
                otaVersion = " ",
            ),
        )

        val error = assertInstanceOf(OtaLookupResult.Error::class.java, result)
        assertEquals(OtaErrorCategory.Device, error.category)
        assertEquals("MissingModel,MissingOtaVersion", error.raw)
        assertEquals(null, transport.lastRequest)
    }

    private fun sampleProfile(): OtaProfile =
        OtaProfile(
            model = "LE2120",
            region = OtaRegion.China,
            otaVersion = "LE2120_11.H.23_0001_000000000001",
            systemType = "Color OS",
            deviceCodename = "OnePlus9Pro_CH",
        )

    private class FakeOtaTransport(
        private val response: OtaHttpResponse? = null,
        private val failure: Exception? = null,
    ) : OtaTransport {
        constructor(failure: IOException) : this(null, failure)

        var lastRequest: LegacyOtaRequest? = null

        override suspend fun post(request: LegacyOtaRequest): OtaHttpResponse {
            lastRequest = request
            failure?.let { throw it }
            return requireNotNull(response)
        }
    }
}
