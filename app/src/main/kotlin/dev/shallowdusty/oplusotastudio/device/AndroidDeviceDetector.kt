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
    private val localeCountryProvider: () -> String = { Locale.getDefault().country },
) : DeviceDetector {

    override suspend fun detect(): DeviceProfile {
        val facts = buildFactsProvider()
        val otaVersion = firstProperty(
            "ro.build.version.ota",
            "ro.oppo.version",
            "ro.build.version.opporom",
        ) ?: facts.display?.takeIf { looksLikeBuildString(it) }

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
        )
    }

    private fun firstProperty(vararg keys: String): String? =
        keys.firstNotNullOfOrNull { key -> propertyProvider.get(key)?.takeIf { it.isNotBlank() } }

    private fun resolveRegion(): OtaRegion {
        val region = firstProperty("ro.oppo.region")?.uppercase(Locale.ROOT)
            ?: localeCountryProvider().uppercase(Locale.ROOT)
        return when (region) {
            "CN", "CHINA" -> OtaRegion.China
            "IN", "INDIA" -> OtaRegion.India
            "EU", "SEA", "SG", "ID", "MY", "TH", "VN" -> OtaRegion.International
            else -> OtaRegion.Global
        }
    }

    private fun looksLikeBuildString(value: String): Boolean =
        value.contains('_') || value.contains('.')
}
