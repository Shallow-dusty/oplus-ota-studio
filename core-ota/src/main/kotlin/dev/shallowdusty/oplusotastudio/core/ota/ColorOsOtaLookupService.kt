package dev.shallowdusty.oplusotastudio.core.ota

import dev.shallowdusty.oplusotastudio.core.model.OtaErrorCategory
import dev.shallowdusty.oplusotastudio.core.model.OtaEvidenceLevel
import dev.shallowdusty.oplusotastudio.core.model.OtaLookupResult
import dev.shallowdusty.oplusotastudio.core.model.OtaLookupService
import dev.shallowdusty.oplusotastudio.core.model.OtaProfile
import dev.shallowdusty.oplusotastudio.core.model.validationErrors
import java.io.IOException

class ColorOsOtaLookupService(
    private val protocol: ColorOsOtaProtocol = ColorOsOtaProtocol(),
    private val transport: ColorOsOtaTransport = OkHttpColorOsOtaTransport(),
    private val evidenceLevel: OtaEvidenceLevel = OtaEvidenceLevel.ReplayedRealProfile,
) : OtaLookupService {

    override suspend fun lookup(profile: OtaProfile): OtaLookupResult {
        val validationErrors = profile.validationErrors()
        if (validationErrors.isNotEmpty()) {
            return OtaLookupResult.Error(
                category = OtaErrorCategory.Device,
                raw = validationErrors.joinToString(",") { it.name },
            )
        }
        val request = protocol.buildRequest(profile)
        return try {
            val response = transport.post(request)
            if (response.statusCode in 200..299) {
                protocol.parseResponse(
                    request = request,
                    rawJson = response.body,
                    evidenceLevel = evidenceLevel,
                )
            } else {
                OtaLookupResult.Error(
                    category = OtaErrorCategory.Server,
                    raw = "HTTP ${response.statusCode}: ${response.body}",
                )
            }
        } catch (error: IOException) {
            OtaLookupResult.Error(OtaErrorCategory.Network, error.message)
        } catch (error: RuntimeException) {
            OtaLookupResult.Error(OtaErrorCategory.Unknown, error.message)
        }
    }
}
