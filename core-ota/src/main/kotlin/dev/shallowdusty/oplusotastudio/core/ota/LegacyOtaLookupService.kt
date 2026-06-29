package dev.shallowdusty.oplusotastudio.core.ota

import dev.shallowdusty.oplusotastudio.core.model.OtaErrorCategory
import dev.shallowdusty.oplusotastudio.core.model.OtaLookupResult
import dev.shallowdusty.oplusotastudio.core.model.OtaLookupService
import dev.shallowdusty.oplusotastudio.core.model.OtaProfile
import java.io.IOException

class LegacyOtaLookupService(
    private val protocol: LegacyOtaProtocol = LegacyOtaProtocol(),
    private val transport: OtaTransport,
) : OtaLookupService {

    override suspend fun lookup(profile: OtaProfile): OtaLookupResult {
        val request = protocol.buildRequest(profile)
        return try {
            val response = transport.post(request)
            if (response.statusCode in 200..299) {
                protocol.parseResponse(
                    rawXml = response.body,
                    sourceHost = response.sourceHost.ifBlank { request.host },
                )
            } else {
                OtaLookupResult.Error(
                    category = OtaErrorCategory.Server,
                    raw = "HTTP ${response.statusCode}: ${response.body}",
                )
            }
        } catch (error: IOException) {
            OtaLookupResult.Error(
                category = OtaErrorCategory.Network,
                raw = error.message,
            )
        } catch (error: RuntimeException) {
            OtaLookupResult.Error(
                category = OtaErrorCategory.Unknown,
                raw = error.message,
            )
        }
    }
}
