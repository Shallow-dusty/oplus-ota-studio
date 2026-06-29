package dev.shallowdusty.oplusotastudio.core.ota

import okhttp3.HttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.HttpUrl.Companion.toHttpUrl

class OkHttpOtaTransport(
    private val client: OkHttpClient = OkHttpClient(),
    private val baseUrlOverride: HttpUrl? = null,
) : OtaTransport {

    override suspend fun post(request: LegacyOtaRequest): OtaHttpResponse {
        val url = baseUrlOverride
            ?.newBuilder()
            ?.encodedPath(request.path)
            ?.build()
            ?: "https://${request.host}${request.path}".toHttpUrl()

        val httpRequest = Request.Builder()
            .url(url)
            .header("Host", request.host)
            .post(request.body.toRequestBody(request.contentType.toMediaType()))
            .build()

        client.newCall(httpRequest).execute().use { response ->
            return OtaHttpResponse(
                statusCode = response.code,
                body = response.body.string(),
                sourceHost = request.host,
            )
        }
    }
}
