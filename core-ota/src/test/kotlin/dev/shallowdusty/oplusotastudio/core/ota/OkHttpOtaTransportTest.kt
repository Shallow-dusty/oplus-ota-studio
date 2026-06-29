package dev.shallowdusty.oplusotastudio.core.ota

import kotlinx.coroutines.test.runTest
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.OkHttpClient
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class OkHttpOtaTransportTest {

    private val server = MockWebServer()

    @AfterEach
    fun tearDown() {
        server.close()
    }

    @Test
    fun `posts legacy request and returns HTTP response`() = runTest {
        server.enqueue(
            MockResponse(
                code = 201,
                body = "<root><Command>NO_NEW_VERSION</Command></root>",
            ),
        )
        server.start()
        val transport = OkHttpOtaTransport(
            client = OkHttpClient(),
            baseUrlOverride = server.url("/"),
        )

        val response = transport.post(
            LegacyOtaRequest(
                host = "otacn.oppo.com",
                path = "/OnePlusOTA/OnePlus_OTA.php",
                contentType = "application/x-www-form-urlencoded",
                body = "systemType=Color+OS&otaVersion=LE2120",
            ),
        )

        val recorded = server.takeRequest()
        assertEquals(201, response.statusCode)
        assertEquals("<root><Command>NO_NEW_VERSION</Command></root>", response.body)
        assertEquals("otacn.oppo.com", response.sourceHost)
        assertEquals("POST", recorded.method)
        assertEquals("/OnePlusOTA/OnePlus_OTA.php", recorded.target)
        assertTrue(recorded.headers["Content-Type"]?.startsWith("application/x-www-form-urlencoded") == true)
        assertEquals("otacn.oppo.com", recorded.headers["Host"])
        assertEquals("systemType=Color+OS&otaVersion=LE2120", recorded.body?.utf8())
    }
}
