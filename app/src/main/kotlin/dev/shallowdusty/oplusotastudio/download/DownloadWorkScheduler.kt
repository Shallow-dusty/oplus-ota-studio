package dev.shallowdusty.oplusotastudio.download

import androidx.work.OneTimeWorkRequest
import androidx.work.ExistingWorkPolicy
import androidx.work.WorkManager
import dev.shallowdusty.oplusotastudio.core.model.DownloadPreferencesStore
import kotlinx.coroutines.flow.first

class DownloadWorkScheduler(
    private val preferencesStore: DownloadPreferencesStore,
    private val enqueuer: DownloadWorkEnqueuer,
    private val requestFactory: DownloadWorkRequestFactory = DownloadWorkRequestFactory(),
) : DownloadTaskWorkScheduler {

    override suspend fun schedule(taskId: String) {
        val preferences = preferencesStore.preferences.first()
        enqueuer.enqueue(taskId, requestFactory.create(taskId, preferences))
    }

    override fun cancel(taskId: String) {
        enqueuer.cancel(taskId)
    }
}

interface DownloadTaskWorkScheduler {
    suspend fun schedule(taskId: String)
    fun cancel(taskId: String)
}

interface DownloadWorkEnqueuer {
    fun enqueue(taskId: String, request: OneTimeWorkRequest)
    fun cancel(taskId: String)
}

class WorkManagerDownloadWorkEnqueuer(
    private val workManager: WorkManager,
) : DownloadWorkEnqueuer {
    override fun enqueue(taskId: String, request: OneTimeWorkRequest) {
        workManager.enqueueUniqueWork(
            uniqueWorkName(taskId),
            ExistingWorkPolicy.KEEP,
            request,
        )
    }

    override fun cancel(taskId: String) {
        workManager.cancelUniqueWork(uniqueWorkName(taskId))
    }

    private fun uniqueWorkName(taskId: String): String {
        return "${DownloadWorker.WorkTag}-$taskId"
    }
}
