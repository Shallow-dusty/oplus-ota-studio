package dev.shallowdusty.oplusotastudio.device

import android.os.Build
import dev.shallowdusty.oplusotastudio.core.model.DeviceDetector
import dev.shallowdusty.oplusotastudio.core.model.DeviceProfile
import dev.shallowdusty.oplusotastudio.core.model.OtaRegion
import java.util.Locale

data class AndroidBuildFacts(
    val model: String?,
    val product: String?,
    val display: String?,
    val androidVersion: String?,
    val securityPatch: String?,
)

fun interface DevicePropertyProvider {
    fun get(key: String): String?
}

class MapDevicePropertyProvider(
    vararg entries: Pair<String, String>,
) : DevicePropertyProvider {
    private val values = mapOf(*entries)
    override fun get(key: String): String? = values[key]
}

class AndroidSystemPropertyProvider : DevicePropertyProvider {
    override fun get(key: String): String? =
        runCatching {
            val systemProperties = Class.forName("android.os.SystemProperties")
            val get = systemProperties.getMethod("get", String::class.java)
            (get.invoke(null, key) as? String)?.takeIf { it.isNotBlank() }
        }.getOrNull()
}

class AndroidDeviceDetector(
    private val buildFactsProvider: () -> AndroidBuildFacts = {
        AndroidBuildFacts(
            model = Build.MODEL,
            product = Build.PRODUCT,
            display = Build.DISPLAY,
            androidVersion = Build.VERSION.RELEASE,
            securityPatch = Build.VERSION.SECURITY_PATCH,
        )
    },
    private val propertyProvider: DevicePropertyProvider = AndroidSystemPropertyProvider(),
    private val deviceIdProvider: () -> String? = { null },
    private val languageTagProvider: () -> String? = { Locale.getDefault().toLanguageTag() },
    private val localeCountryProvider: () -> String = { Locale.getDefault().country },
) : DeviceDetector {

    override suspend fun detect(): DeviceProfile {
        val facts = buildFactsProvider()
        val otaVersion = firstProperty(
            "ro.build.version.ota",
            "ro.oppo.version",
            "ro.build.version.opporom",
        ) ?: parseDisplayOtaVersion(facts.display)

        return DeviceProfile(
            model = facts.model,
            product = facts.product,
            marketingName = firstProperty("ro.oppo.market.name", "ro.product.marketname"),
            otaVersion = otaVersion,
            buildDisplay = facts.display,
            androidVersion = facts.androidVersion,
            securityPatch = facts.securityPatch,
            region = resolveRegion(),
            serialSuffix = null,
            incomplete = facts.model.isNullOrBlank() || otaVersion.isNullOrBlank(),
            nvCarrier = firstProperty("ro.build.oplus_nv_id"),
            deviceId = deviceIdProvider()?.takeIf { it.isNotBlank() },
            language = languageTagProvider()?.takeIf { it.isNotBlank() },
        )
    }

    private fun firstProperty(vararg keys: String): String? =
        keys.firstNotNullOfOrNull { key -> propertyProvider.get(key)?.takeIf { it.isNotBlank() } }

    private fun resolveRegion(): OtaRegion {
        val region = firstProperty("ro.oppo.region", "persist.sys.oplus.region")?.uppercase(Locale.ROOT)
            ?: localeCountryProvider().uppercase(Locale.ROOT)
        return when (region) {
            "CN", "CHINA" -> OtaRegion.China
            "IN", "INDIA" -> OtaRegion.India
            "EU", "SEA", "SG", "ID", "MY", "TH", "VN" -> OtaRegion.International
            else -> OtaRegion.Global
        }
    }

    private fun parseDisplayOtaVersion(value: String?): String? {
        val display = value?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        DisplayFullBuildPattern.find(display)?.let { return it.value }
        ColorOsDisplayVersionPattern.find(display)?.let { return it.value }
        DisplayVersionWithSuffixPattern.find(display)?.let { return it.value }
        val version = DisplayVersionPattern.find(display)?.value ?: return null
        val suffix = DisplayBuildSuffixPattern.find(display)?.value
        return if (suffix != null) "$version.$suffix" else version
    }

    private companion object {
        val DisplayFullBuildPattern = Regex("""\b[A-Z]{2}\d{4}_[A-Za-z0-9.]+(?:_[A-Za-z0-9.]+)+\b""")
        val ColorOsDisplayVersionPattern = Regex("""\b[A-Z]{2}\d{4}_\d+(?:\.\d+){2,}\([A-Z0-9]+\)(?=\s|$)""")
        val DisplayVersionWithSuffixPattern = Regex("""(?<![0-9.])\d+(?:\.\d+){2,}\.[A-Z]{2}\d{2}[A-Z]{2}\b""")
        val DisplayVersionPattern = Regex("""(?<![0-9.])\d+(?:\.\d+){2,}(?![0-9.])""")
        val DisplayBuildSuffixPattern = Regex("""(?<![A-Z0-9])[A-Z]{2}\d{2}[A-Z]{2}(?![A-Z0-9])""")
    }
}
