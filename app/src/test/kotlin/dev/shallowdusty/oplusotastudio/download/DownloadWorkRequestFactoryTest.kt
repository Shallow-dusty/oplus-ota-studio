package dev.shallowdusty.oplusotastudio.download

import androidx.work.NetworkType
import dev.shallowdusty.oplusotastudio.core.model.DownloadPreferences
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class DownloadWorkRequestFactoryTest {

    @Test
    fun `uses unmetered network when wifi only is enabled`() {
        val request = DownloadWorkRequestFactory().create(
            taskId = "task-1",
            preferences = DownloadPreferences(wifiOnly = true),
        )

        assertEquals(NetworkType.UNMETERED, request.workSpec.constraints.requiredNetworkType)
        assertTrue(request.workSpec.constraints.requiresBatteryNotLow())
        assertEquals("task-1", request.workSpec.input.getString(DownloadWorker.TaskIdKey))
        assertTrue(request.tags.contains(DownloadWorker.WorkTag))
    }

    @Test
    fun `uses connected network when wifi only is disabled`() {
        val request = DownloadWorkRequestFactory().create(
            taskId = "task-2",
            preferences = DownloadPreferences(wifiOnly = false),
        )

        assertEquals(NetworkType.CONNECTED, request.workSpec.constraints.requiredNetworkType)
        assertEquals("task-2", request.workSpec.input.getString(DownloadWorker.TaskIdKey))
    }
}
