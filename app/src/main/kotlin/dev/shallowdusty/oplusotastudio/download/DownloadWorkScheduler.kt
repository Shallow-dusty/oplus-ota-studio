package dev.shallowdusty.oplusotastudio.download

import androidx.work.OneTimeWorkRequest
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
        enqueuer.enqueue(requestFactory.create(taskId, preferences))
    }
}

interface DownloadTaskWorkScheduler {
    suspend fun schedule(taskId: String)
}

interface DownloadWorkEnqueuer {
    fun enqueue(request: OneTimeWorkRequest)
}

class WorkManagerDownloadWorkEnqueuer(
    private val workManager: WorkManager,
) : DownloadWorkEnqueuer {
    override fun enqueue(request: OneTimeWorkRequest) {
        workManager.enqueue(request)
    }
}
