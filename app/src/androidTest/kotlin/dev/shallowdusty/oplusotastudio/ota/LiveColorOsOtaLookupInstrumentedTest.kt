package dev.shallowdusty.oplusotastudio.ota

import android.provider.Settings
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.shallowdusty.oplusotastudio.core.model.OtaEvidenceLevel
import dev.shallowdusty.oplusotastudio.core.model.OtaLookupResult
import dev.shallowdusty.oplusotastudio.core.model.OtaProfile
import dev.shallowdusty.oplusotastudio.core.model.OtaRegion
import dev.shallowdusty.oplusotastudio.core.ota.ColorOsOtaLookupService
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LiveColorOsOtaLookupInstrumentedTest {

    @Test
    fun queriesOnePlus9ProCnColorOsEndpoint() = runBlocking {
        val args = InstrumentationRegistry.getArguments()
        assumeTrue("Pass liveOta=true to run live OTA endpoint evidence.", args.getString("liveOta") == "true")
        val context = InstrumentationRegistry.getInstrumentation().targetContext

        val profile = OtaProfile(
            model = args.getString("model") ?: "LE2120",
            region = OtaRegion.China,
            otaVersion = args.getString("otaVersion") ?: "LE2120_11.H.23_0001_000000000001",
            systemType = "Color OS",
            deviceCodename = args.getString("deviceCodename") ?: "OnePlus9Pro_CH",
            nvCarrier = args.getString("nvCarrier") ?: "10010111",
            deviceId = args.getString("deviceId")
                ?: Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID),
            language = args.getString("language") ?: "zh-Hans-CN",
        )

        val result = ColorOsOtaLookupService().lookup(profile)

        assertTrue("Expected PackageFound, got $result", result is OtaLookupResult.PackageFound)
        val pkg = (result as OtaLookupResult.PackageFound).pkg
        assertEquals("LE2120_14.0.0.1901(CN01)", pkg.versionName)
        assertEquals(6_559_817_109L, pkg.sizeBytes)
        assertEquals("5ae1e4d8101218d58c1da10092b22996", pkg.md5)
        assertEquals("component-otapc-cn.allawntech.com", pkg.sourceHost)
        assertEquals(OtaEvidenceLevel.ReplayedRealProfile, pkg.evidenceLevel)
    }
}
