package dev.shallowdusty.oplusotastudio.core.ota

import java.util.concurrent.Executors
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.test.runTest
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
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

    @Test
    fun `posts legacy request on injected IO dispatcher`() = runTest {
        val executionThreads = mutableListOf<String>()
        val client = OkHttpClient.Builder()
            .addInterceptor { chain ->
                executionThreads += Thread.currentThread().name
                Response.Builder()
                    .request(chain.request())
                    .protocol(Protocol.HTTP_1_1)
                    .code(200)
                    .message("OK")
                    .body("<root/>".toResponseBody())
                    .build()
            }
            .build()
        val ioDispatcher = Executors
            .newSingleThreadExecutor { runnable -> Thread(runnable, "legacy-ota-io") }
            .asCoroutineDispatcher()
        try {
            val transport = OkHttpOtaTransport(
                client = client,
                baseUrlOverride = "https://example.test/".toHttpUrl(),
                ioDispatcher = ioDispatcher,
            )

            transport.post(
                LegacyOtaRequest(
                    host = "otacn.oppo.com",
                    path = "/OnePlusOTA/OnePlus_OTA.php",
                    contentType = "application/x-www-form-urlencoded",
                    body = "systemType=Color+OS&otaVersion=LE2120",
                ),
            )
        } finally {
            ioDispatcher.close()
        }

        assertEquals(listOf("legacy-ota-io"), executionThreads)
    }
}
