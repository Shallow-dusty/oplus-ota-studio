package dev.shallowdusty.oplusotastudio.download

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class DownloadForegroundNotificationTest {

    @Test
    fun `uses stable notification metadata for download foreground work`() {
        assertEquals("download_progress", DownloadForegroundNotification.ChannelId)
        assertEquals(2010, DownloadForegroundNotification.NotificationId)
    }
}
