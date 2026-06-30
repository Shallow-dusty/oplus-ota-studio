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
            executionResult = null,
        )

    companion object {
        const val TaskIdKey = "task_id"
        const val WorkTag = "ota-download"
    }
}

interface DownloadWorkerExecutor {
    suspend fun execute(taskId: String): DownloadWorkerExecutionResult
}

enum class DownloadWorkerExecutionResult {
    Succeeded,
    Retry,
    Failed,
}

object DownloadWorkerResultPolicy {
    fun resultFor(
        taskId: String?,
        executionResult: DownloadWorkerExecutionResult?,
    ): ListenableWorker.Result {
        if (taskId.isNullOrBlank()) return ListenableWorker.Result.failure()
        return when (executionResult) {
            DownloadWorkerExecutionResult.Succeeded -> ListenableWorker.Result.success()
            DownloadWorkerExecutionResult.Retry -> ListenableWorker.Result.retry()
            DownloadWorkerExecutionResult.Failed,
            null,
            -> ListenableWorker.Result.failure()
        }
    }
}
