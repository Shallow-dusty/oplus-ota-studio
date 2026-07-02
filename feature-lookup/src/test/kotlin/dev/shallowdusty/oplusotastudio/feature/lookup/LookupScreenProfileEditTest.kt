package dev.shallowdusty.oplusotastudio.feature.lookup

import dev.shallowdusty.oplusotastudio.core.model.OtaProfile
import dev.shallowdusty.oplusotastudio.core.model.OtaRegion
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class LookupScreenProfileEditTest {

    @Test
    fun `manual model edit clears detected device codename`() {
        val detectedProfile = OtaProfile(
            model = "LE2120",
            region = OtaRegion.China,
            otaVersion = "LE2120_14.0.0.1901(CN01)",
            deviceCodename = "lemonade",
        )

        val edited = detectedProfile.withManualModel("CPH2417")

        assertEquals("CPH2417", edited.model)
        assertEquals(null, edited.deviceCodename)
    }
}
