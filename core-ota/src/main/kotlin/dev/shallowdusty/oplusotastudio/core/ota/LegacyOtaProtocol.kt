package dev.shallowdusty.oplusotastudio.core.ota

import dev.shallowdusty.oplusotastudio.core.model.OtaErrorCategory
import dev.shallowdusty.oplusotastudio.core.model.OtaLookupResult
import dev.shallowdusty.oplusotastudio.core.model.OtaPackage
import dev.shallowdusty.oplusotastudio.core.model.OtaProfile
import java.io.StringReader
import java.net.URLEncoder
import javax.xml.parsers.DocumentBuilderFactory
import org.xml.sax.InputSource

data class LegacyOtaRequest(
    val host: String,
    val path: String,
    val contentType: String,
    val body: String,
)

class LegacyOtaProtocol(
    private val hostResolver: OtaHostResolver = OtaHostResolver(),
) {
    fun buildRequest(profile: OtaProfile): LegacyOtaRequest {
        val form = linkedMapOf(
            "systemType" to (profile.systemType ?: "Color OS"),
            "otaVersion" to profile.otaVersion,
            "mode" to "full",
            "device" to (profile.deviceCodename ?: profile.model),
        )
        return LegacyOtaRequest(
            host = hostResolver.resolve(profile.region),
            path = "/OnePlusOTA/OnePlus_OTA.php",
            contentType = "application/x-www-form-urlencoded",
            body = form.entries.joinToString("&") { (key, value) ->
                "${encode(key)}=${encode(value)}"
            },
        )
    }

    fun parseResponse(rawXml: String, sourceHost: String): OtaLookupResult =
        runCatching {
            val document = secureDocumentBuilderFactory()
                .newDocumentBuilder()
                .parse(InputSource(StringReader(rawXml)))

            when (document.text("Command")) {
                "NO_NEW_VERSION" -> OtaLookupResult.NoUpdate
                "NEW_VERSION" -> parsePackage(document, sourceHost, rawXml)
                else -> OtaLookupResult.Error(OtaErrorCategory.Malformed, rawXml)
            }
        }.getOrElse {
            OtaLookupResult.Error(OtaErrorCategory.Malformed, rawXml)
        }

    private fun parsePackage(
        document: org.w3c.dom.Document,
        sourceHost: String,
        rawXml: String,
    ): OtaLookupResult {
        val versionName = document.text("versionName")
            ?: return OtaLookupResult.Error(OtaErrorCategory.Malformed, rawXml)
        val size = document.text("size")?.toLongOrNull()
            ?: return OtaLookupResult.Error(OtaErrorCategory.Malformed, rawXml)
        val url = document.text("url")
            ?: return OtaLookupResult.Error(OtaErrorCategory.Malformed, rawXml)

        return OtaLookupResult.PackageFound(
            OtaPackage(
                versionName = versionName,
                type = document.text("type"),
                sizeBytes = size,
                sourceHost = sourceHost,
                downloadUrl = url,
                md5 = document.text("md5"),
                sha256 = document.text("sha256"),
                releaseNotes = document.text("releaseNotes"),
            ),
        )
    }

    private fun encode(value: String): String =
        URLEncoder.encode(value, Charsets.UTF_8.name())

    private fun secureDocumentBuilderFactory(): DocumentBuilderFactory =
        DocumentBuilderFactory.newInstance().apply {
            isIgnoringComments = true
            isCoalescing = true
            setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
            setFeature("http://xml.org/sax/features/external-general-entities", false)
            setFeature("http://xml.org/sax/features/external-parameter-entities", false)
            setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false)
            isXIncludeAware = false
            isExpandEntityReferences = false
        }
}

private fun org.w3c.dom.Document.text(tagName: String): String? {
    val nodes = getElementsByTagName(tagName)
    if (nodes.length == 0) return null
    return nodes.item(0).textContent.trim().takeIf { it.isNotEmpty() }
}
