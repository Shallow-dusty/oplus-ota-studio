package dev.shallowdusty.oplusotastudio.core.ota

import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.MediaType.Companion.toMediaType

interface ColorOsOtaTransport {
    suspend fun post(request: ColorOsOtaRequest): OtaHttpResponse
}

class OkHttpColorOsOtaTransport(
    private val client: OkHttpClient = OkHttpClient(),
) : ColorOsOtaTransport {

    override suspend fun post(request: ColorOsOtaRequest): OtaHttpResponse {
        val httpRequestBuilder = Request.Builder()
            .url(request.url)
            .post(request.body.toRequestBody(request.contentType.toMediaType()))
        request.headers.forEach { (name, value) ->
            httpRequestBuilder.header(name, value)
        }
        client.newCall(httpRequestBuilder.build()).execute().use { response ->
            return OtaHttpResponse(
                statusCode = response.code,
                body = response.body.string(),
                sourceHost = request.host,
            )
        }
    }
}
