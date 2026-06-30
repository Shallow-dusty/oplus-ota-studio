package dev.shallowdusty.oplusotastudio.download

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ListenableWorker
import androidx.work.WorkerParameters

class DownloadWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result =
        DownloadWorkerResultPolicy.resultFor(
            taskId = inputData.getString(TaskIdKey),
            executionBackendAvailable = false,
        )

    companion object {
        const val TaskIdKey = "task_id"
        const val WorkTag = "ota-download"
    }
}

object DownloadWorkerResultPolicy {
    fun resultFor(
        taskId: String?,
        executionBackendAvailable: Boolean,
    ): ListenableWorker.Result {
        if (taskId.isNullOrBlank()) return ListenableWorker.Result.failure()
        if (!executionBackendAvailable) return ListenableWorker.Result.failure()
        return ListenableWorker.Result.success()
    }
}
