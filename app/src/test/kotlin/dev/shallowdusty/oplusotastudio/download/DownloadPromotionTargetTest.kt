package dev.shallowdusty.oplusotastudio.download

import dev.shallowdusty.oplusotastudio.core.model.OtaPackage
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class DownloadPromotionTargetTest {

    @Test
    fun `fromPackage creates zip display name under app downloads directory`() {
        val target = DownloadPromotionTarget.fromPackage(
            OtaPackage(
                versionName = "LE2120_14.0.0.1901(CN01)",
                type = "full",
                sizeBytes = 123L,
                sourceHost = "otagm.oppo.com",
                downloadUrl = "https://otagm.oppo.com/path/package.zip",
                md5 = null,
            ),
        )

        assertEquals("OPlus OTA Studio", target.directoryName)
        assertEquals("LE2120_14.0.0.1901_CN01_full.zip", target.displayName)
        assertEquals("application/zip", target.mimeType)
    }

    @Test
    fun `fromPackage falls back to package url name when version is blank`() {
        val target = DownloadPromotionTarget.fromPackage(
            OtaPackage(
                versionName = "",
                type = null,
                sizeBytes = 123L,
                sourceHost = "otagm.oppo.com",
                downloadUrl = "https://otagm.oppo.com/path/update payload.zip?token=1",
                md5 = null,
            ),
        )

        assertEquals("update_payload.zip", target.displayName)
    }
}
