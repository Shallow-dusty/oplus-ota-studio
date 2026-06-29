package dev.shallowdusty.oplusotastudio.core.ota

interface OtaTransport {
    suspend fun post(request: LegacyOtaRequest): OtaHttpResponse
}

data class OtaHttpResponse(
    val statusCode: Int,
    val body: String,
    val sourceHost: String,
)
