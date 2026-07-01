package dev.shallowdusty.oplusotastudio.core.ota

import dev.shallowdusty.oplusotastudio.core.model.OtaErrorCategory
import dev.shallowdusty.oplusotastudio.core.model.OtaLookupResult
import dev.shallowdusty.oplusotastudio.core.model.OtaProfile
import dev.shallowdusty.oplusotastudio.core.model.OtaRegion

data class JsonOtaRequest(
    val host: String,
    val path: String,
    val contentType: String,
    val body: String,
)

/**
 * Disabled OPlus/ColorOS JSON protocol skeleton.
 *
 * Spec §1.2 requires the architecture to support multiple OTA protocol styles,
 * but JSON request/response fields are still unverified. Keep this strategy
 * testable and explicit instead of enabling synthetic-only live lookups.
 */
class JsonOtaProtocol(
    private val hostResolver: OtaHostResolver = OtaHostResolver(),
) {
    val isEnabled: Boolean = false

    fun buildRequest(profile: OtaProfile): JsonOtaRequest {
        val fields = linkedMapOf(
            "model" to profile.model,
            "region" to profile.region.protocolCode,
            "otaVersion" to profile.otaVersion,
            "systemType" to (profile.systemType ?: "Color OS"),
        )
        return JsonOtaRequest(
            host = profile.hostOverride
                ?.trim()
                ?.takeIf { it.isNotEmpty() }
                ?: hostResolver.resolve(profile.region),
            path = DisabledPath,
            contentType = "application/json",
            body = fields.entries.joinToString(
                separator = ",",
                prefix = "{",
                postfix = "}",
            ) { (key, value) ->
                """"${key.escapeJson()}":"${value.escapeJson()}""""
            },
        )
    }

    @Suppress("UNUSED_PARAMETER")
    fun parseResponse(rawJson: String, sourceHost: String): OtaLookupResult {
        return OtaLookupResult.Error(
            category = OtaErrorCategory.Server,
            raw = "json-protocol-disabled: $sourceHost",
        )
    }

    private companion object {
        const val DisabledPath = "/ota/json/disabled-unverified"
    }
}

private val OtaRegion.protocolCode: String
    get() = when (this) {
        OtaRegion.Global -> "GLOBAL"
        OtaRegion.India -> "IN"
        OtaRegion.International -> "INTL"
        OtaRegion.China -> "CN"
    }

private fun String.escapeJson(): String =
    buildString(length) {
        for (char in this@escapeJson) {
            when (char) {
                '\\' -> append("\\\\")
                '"' -> append("\\\"")
                '\b' -> append("\\b")
                '\u000C' -> append("\\f")
                '\n' -> append("\\n")
                '\r' -> append("\\r")
                '\t' -> append("\\t")
                else -> append(char)
            }
        }
    }
