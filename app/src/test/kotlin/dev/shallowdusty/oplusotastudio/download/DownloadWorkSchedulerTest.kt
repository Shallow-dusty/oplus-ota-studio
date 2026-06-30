package dev.shallowdusty.oplusotastudio.download

import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequest
import dev.shallowdusty.oplusotastudio.core.model.DownloadPreferences
import dev.shallowdusty.oplusotastudio.core.model.DownloadPreferencesStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class DownloadWorkSchedulerTest {

    @Test
    fun `enqueues work request using latest download preferences`() = runTest {
        val enqueuer = RecordingDownloadWorkEnqueuer()
        val preferencesStore = RecordingDownloadPreferencesStore(
            DownloadPreferences(wifiOnly = false),
        )
        val scheduler = DownloadWorkScheduler(
            preferencesStore = preferencesStore,
            enqueuer = enqueuer,
        )

        scheduler.schedule(taskId = "task-1")

        val request = enqueuer.requests.single()
        assertEquals("task-1", request.workSpec.input.getString(DownloadWorker.TaskIdKey))
        assertEquals(NetworkType.CONNECTED, request.workSpec.constraints.requiredNetworkType)
    }

    private class RecordingDownloadWorkEnqueuer : DownloadWorkEnqueuer {
        val requests = mutableListOf<OneTimeWorkRequest>()

        override fun enqueue(request: OneTimeWorkRequest) {
            requests += request
        }
    }

    private class RecordingDownloadPreferencesStore(
        initialPreferences: DownloadPreferences,
    ) : DownloadPreferencesStore {
        private val state = MutableStateFlow(initialPreferences)

        override val preferences: Flow<DownloadPreferences> = state

        override suspend fun setWifiOnly(enabled: Boolean) {
            state.value = state.value.copy(wifiOnly = enabled)
        }

        override suspend fun setBatteryPauseThresholdPercent(percent: Int) {
            state.value = state.value.copy(batteryPauseThresholdPercent = percent)
        }
    }
}
