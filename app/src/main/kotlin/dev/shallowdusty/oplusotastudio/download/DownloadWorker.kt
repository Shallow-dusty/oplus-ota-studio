package dev.shallowdusty.oplusotastudio.download

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters

class DownloadWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result =
        if (inputData.getString(TaskIdKey).isNullOrBlank()) {
            Result.failure()
        } else {
            Result.success()
        }

    companion object {
        const val TaskIdKey = "task_id"
        const val WorkTag = "ota-download"
    }
}
