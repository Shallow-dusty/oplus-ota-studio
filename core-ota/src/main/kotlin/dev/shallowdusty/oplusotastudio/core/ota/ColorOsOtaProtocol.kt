package dev.shallowdusty.oplusotastudio.core.ota

import dev.shallowdusty.oplusotastudio.core.model.OtaErrorCategory
import dev.shallowdusty.oplusotastudio.core.model.OtaEvidenceLevel
import dev.shallowdusty.oplusotastudio.core.model.OtaLookupResult
import dev.shallowdusty.oplusotastudio.core.model.OtaProfile
import dev.shallowdusty.oplusotastudio.core.model.OtaRegion
import java.net.URI
import java.util.Base64
import org.json.JSONObject

data class ColorOsOtaRequest(
    val url: String,
    val host: String,
    val contentType: String,
    val body: String,
    val headers: Map<String, String>,
    val responseKey: String,
)

class ColorOsOtaProtocol(
    private val crypto: ColorOsCrypto = ColorOsCrypto(),
    private val aesKeyProvider: () -> ByteArray = { ColorOsCrypto().randomBytes(AesKeyBytes) },
    private val ivProvider: () -> ByteArray = { ColorOsCrypto().randomBytes(IvBytes) },
    private val nowMs: () -> Long = { System.currentTimeMillis() },
) {
    val isEnabled: Boolean = true

    fun buildRequest(profile: OtaProfile): ColorOsOtaRequest {
        val config = serverConfig(profile.region)
        val model = profile.deviceCodename?.takeIf { it.isNotBlank() } ?: profile.model
        val otaPrefix = profile.otaVersion.split("_").take(2).joinToString("_")
        val region = profile.region.colorOsCode
        val language = profile.language?.takeIf { it.isNotBlank() }
            ?: if (region == "CN") "zh-CN" else "en-EN"
        val deviceId = profile.deviceId
            ?.takeIf { it.isNotBlank() }
            ?.let(crypto::sha256)
            ?: "0"
        val nvCarrier = profile.nvCarrier?.takeIf { it.isNotBlank() } ?: "unknown"
        val key = aesKeyProvider()
        val iv = ivProvider()
        val responseKey = Base64.getEncoder().encodeToString(key)

        val plainBody = linkedMapOf<String, Any?>(
            "language" to language,
            "romVersion" to otaPrefix,
            "otaVersion" to profile.otaVersion,
            "androidVersion" to "Android14.0",
            "colorOSVersion" to "ColorOS14",
            "model" to model,
            "productName" to model,
            "operator" to model,
            "uRegion" to region,
            "trackRegion" to region,
            "imei" to RedactedImei,
            "imei1" to RedactedImei,
            "mode" to "0",
            "registrationId" to "unknown",
            "deviceId" to deviceId,
            "version" to "3",
            "type" to "1",
            "otaPrefix" to otaPrefix,
            "isRealme" to if (model.contains("RMX", ignoreCase = true)) "1" else "0",
            "time" to nowMs().toString(),
            "canCheckSelf" to "0",
        )
        val cipher = crypto.encryptCtrV2(
            plainText = JSONObject(plainBody).toString(),
            key = key,
            iv = iv,
        )
        val encryptedParams = JSONObject(
            mapOf(
                "cipher" to cipher,
                "iv" to Base64.getEncoder().encodeToString(iv),
            ),
        )
        val body = JSONObject(mapOf("params" to encryptedParams.toString())).toString()
        val headers = defaultHeaders(
            model = model,
            otaVersion = profile.otaVersion,
            otaPrefix = otaPrefix,
            region = region,
            language = language,
            nvCarrier = nvCarrier,
            deviceId = deviceId,
        ).toMutableMap()
        headers["version"] = "2"
        headers["protectedKey"] = JSONObject(
            mapOf(
                "SCENE_1" to mapOf(
                    "protectedKey" to crypto.generateProtectedKey(responseKey, config.publicKey),
                    "version" to nowMs().toString(),
                    "negotiationVersion" to config.negotiationVersion,
                ),
            ),
        ).toString()

        return ColorOsOtaRequest(
            url = config.serverUrl,
            host = requireNotNull(URI(config.serverUrl).host),
            contentType = "application/json",
            body = body,
            headers = headers,
            responseKey = responseKey,
        )
    }

    fun parseResponse(
        request: ColorOsOtaRequest,
        rawJson: String,
        evidenceLevel: OtaEvidenceLevel = OtaEvidenceLevel.Synthetic,
    ): OtaLookupResult =
        runCatching {
            val root = JSONObject(rawJson)
            val responseCode = root.optInt("responseCode", 200)
            if (responseCode == 304) return OtaLookupResult.NoUpdate
            if (responseCode != 200) {
                return OtaLookupResult.Error(
                    category = OtaErrorCategory.Server,
                    raw = "responseCode=$responseCode ${root.optString("errMsg")}".trim(),
                )
            }
            val encryptedBody = root.optString("body").takeIf { it.isNotBlank() }
                ?: return OtaLookupResult.Error(OtaErrorCategory.Malformed, rawJson)
            val body = JSONObject(encryptedBody)
            val decrypted = crypto.decryptCtrV2(
                cipher = body.getString("cipher"),
                key = request.responseKey,
                iv = body.getString("iv"),
            )
            JsonOtaProtocol().parseDecryptedComponentPayload(
                rawJson = decrypted,
                sourceHost = request.host,
                evidenceLevel = evidenceLevel,
            )
        }.getOrElse {
            OtaLookupResult.Error(OtaErrorCategory.Malformed, rawJson)
        }

    private fun defaultHeaders(
        model: String,
        otaVersion: String,
        otaPrefix: String,
        region: String,
        language: String,
        nvCarrier: String,
        deviceId: String,
    ): Map<String, String> =
        linkedMapOf(
            "language" to language,
            "romVersion" to otaPrefix,
            "otaVersion" to otaVersion,
            "androidVersion" to "Android14.0",
            "colorOSVersion" to "ColorOS14",
            "model" to model,
            "infVersion" to "1",
            "operator" to model,
            "nvCarrier" to nvCarrier,
            "uRegion" to region,
            "trackRegion" to region,
            "imei" to RedactedImei,
            "imei1" to RedactedImei,
            "deviceId" to deviceId,
            "mode" to "client_auto",
            "channel" to "pc",
            "Accept" to "application/json",
            "Content-Type" to "application/json",
            "User-Agent" to "NULL",
        )

    private fun serverConfig(region: OtaRegion): ColorOsServerConfig =
        when (region) {
            OtaRegion.China -> ColorOsServerConfig(
                serverUrl = "https://component-otapc-cn.allawntech.com/update/v3",
                publicKey = ChinaPublicKey,
                negotiationVersion = "1615879139745",
            )
            else -> ColorOsServerConfig(
                serverUrl = "https://component-otapc-sg.allawnos.com/update/v3",
                publicKey = GlobalPublicKey,
                negotiationVersion = "1615895993238",
            )
        }

    private data class ColorOsServerConfig(
        val serverUrl: String,
        val publicKey: String,
        val negotiationVersion: String,
    )

    private companion object {
        const val AesKeyBytes = 32
        const val IvBytes = 16
        const val RedactedImei = "000000000000000"
        const val ChinaPublicKey =
            "MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEApXYGXQpNL7gmMzzvajHaoZIHQQvBc2cOEhJc7/tsaO4sT0unoQnwQKfNQCuv7qC1Nu32eCLuewe9LSYhDXr9KSBWjOcCFXVXteLO9WCaAh5hwnUoP/5/Wz0jJwBA+yqs3AaGLA9wJ0+B2lB1vLE4FZNE7exUfwUc03fJxHG9nCLKjIZlrnAAHjRCd8mpnADwfkCEIPIGhnwq7pdkbamZcoZfZud1+fPsELviB9u447C6bKnTU4AaMcR9Y2/uI6TJUTcgyCp+ilgU0JxemrSIPFk3jbCbzamQ6Shkw/jDRzYoXpBRg/2QDkbq+j3ljInu0RHDfOeXf3VBfHSnQ66HCwIDAQAB"
        const val GlobalPublicKey =
            "MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEAkA980wxi+eTGcFDiw2I6RrUeO4jL/Aj3Yw4dNuW7tYt+O1sRTHgrzxPD9SrOqzz7G0KgoSfdFHe3JVLPN+U1waK+T0HfLusVJshDaMrMiQFDUiKajb+QKr+bXQhVofH74fjat+oRJ8vjXARSpFk4/41x5j1Bt/2bHoqtdGPcUizZ4whMwzap+hzVlZgs7BNfepo24PWPRujsN3uopl+8u4HFpQDlQl7GdqDYDj2zNOHdFQI2UpSf0aIeKCKOpSKF72KDEESpJVQsqO4nxMwEi2jMujQeCHyTCjBZ+W35RzwT9+0pyZv8FB3c7FYY9FdF/+lvfax5mvFEBd9jO+dpMQIDAQAB"
    }
}

private val OtaRegion.colorOsCode: String
    get() = when (this) {
        OtaRegion.China -> "CN"
        OtaRegion.India -> "IN"
        OtaRegion.International -> "EU"
        OtaRegion.Global -> "GL"
    }
