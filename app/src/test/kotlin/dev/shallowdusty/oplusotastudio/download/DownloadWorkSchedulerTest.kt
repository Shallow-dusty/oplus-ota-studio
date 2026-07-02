package dev.shallowdusty.oplusotastudio.download

import androidx.work.ExistingWorkPolicy
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

        val request = enqueuer.enqueued.single().request
        assertEquals("task-1", enqueuer.enqueued.single().taskId)
        assertEquals(ExistingWorkPolicy.REPLACE, enqueuer.enqueued.single().policy)
        assertEquals("task-1", request.workSpec.input.getString(DownloadWorker.TaskIdKey))
        assertEquals(NetworkType.CONNECTED, request.workSpec.constraints.requiredNetworkType)
    }

    @Test
    fun `recovers work without replacing an existing unique request`() = runTest {
        val enqueuer = RecordingDownloadWorkEnqueuer()
        val scheduler = DownloadWorkScheduler(
            preferencesStore = RecordingDownloadPreferencesStore(DownloadPreferences()),
            enqueuer = enqueuer,
        )

        scheduler.recover(taskId = "task-1")

        assertEquals("task-1", enqueuer.enqueued.single().taskId)
        assertEquals(ExistingWorkPolicy.KEEP, enqueuer.enqueued.single().policy)
    }

    @Test
    fun `cancels work by task id`() {
        val enqueuer = RecordingDownloadWorkEnqueuer()
        val scheduler = DownloadWorkScheduler(
            preferencesStore = RecordingDownloadPreferencesStore(DownloadPreferences()),
            enqueuer = enqueuer,
        )

        scheduler.cancel(taskId = "task-1")

        assertEquals(listOf("task-1"), enqueuer.canceled)
    }

    private class RecordingDownloadWorkEnqueuer : DownloadWorkEnqueuer {
        val enqueued = mutableListOf<EnqueuedRequest>()
        val canceled = mutableListOf<String>()

        override fun enqueue(
            taskId: String,
            request: OneTimeWorkRequest,
            policy: ExistingWorkPolicy,
        ) {
            enqueued += EnqueuedRequest(taskId, request, policy)
        }

        override fun cancel(taskId: String) {
            canceled += taskId
        }
    }

    private data class EnqueuedRequest(
        val taskId: String,
        val request: OneTimeWorkRequest,
        val policy: ExistingWorkPolicy,
    )

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
