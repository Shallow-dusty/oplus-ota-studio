package dev.shallowdusty.oplusotastudio.core.ota

import dev.shallowdusty.oplusotastudio.core.model.OtaRegion
import java.util.Properties

data class OtaHostMap(
    val version: Int,
    val hosts: Map<OtaRegion, String>,
) {
    companion object {
        fun loadBuiltIn(): OtaHostMap =
            OtaHostMap::class.java.getResourceAsStream("/oplus-ota-hosts.properties")
                ?.use(::fromProperties)
                ?: fallback()

        private fun fromProperties(input: java.io.InputStream): OtaHostMap {
            val properties = Properties().apply { load(input) }
            val hosts = OtaRegion.entries.mapNotNull { region ->
                properties.getProperty("host.${region.name.lowercase()}")
                    ?.takeIf { it.isNotBlank() }
                    ?.let { region to it.trim() }
            }.toMap()
            return OtaHostMap(
                version = properties.getProperty("version")?.toIntOrNull() ?: 0,
                hosts = hosts.ifEmpty { fallback().hosts },
            )
        }

        private fun fallback(): OtaHostMap =
            OtaHostMap(
                version = 0,
                hosts = mapOf(
                    OtaRegion.Global to "otagm.oppo.com",
                    OtaRegion.India to "otadiu.oppo.com",
                    OtaRegion.International to "otai.oppo.com",
                    OtaRegion.China to "otacn.oppo.com",
                ),
            )
    }
}

class OtaHostResolver(
    private val hostMap: OtaHostMap = OtaHostMap.loadBuiltIn(),
) {
    val version: Int = hostMap.version

    fun resolve(region: OtaRegion): String =
        hostMap.hosts[region]
            ?: hostMap.hosts[OtaRegion.Global]
            ?: error("OTA host map does not define a global fallback host")
}
