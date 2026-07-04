package dev.shallowdusty.oplusotastudio.download

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ListenableWorker
import androidx.work.WorkerParameters
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine

class DownloadWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val taskId = inputData.getString(TaskIdKey)
        taskId
            ?.takeUnless { it.isBlank() }
            ?.let { setForeground(DownloadForegroundInfoFactory(applicationContext).create(it)) }
        val executionResult = taskId
            ?.takeUnless { it.isBlank() }
            ?.let { activeTaskId ->
                val executor = (applicationContext as? DownloadWorkerExecutorProvider)
                    ?.downloadWorkerExecutor
                executor?.executeWithStopOnCancellation(activeTaskId)
            }
        return DownloadWorkerResultPolicy.resultFor(
            taskId = taskId,
            executionResult = executionResult,
        )
    }

    companion object {
        const val TaskIdKey = "task_id"
        const val WorkTag = "ota-download"
    }

    private suspend fun DownloadWorkerExecutor.executeWithStopOnCancellation(
        taskId: String,
    ): DownloadWorkerExecutionResult =
        coroutineScope {
            suspendCancellableCoroutine { continuation ->
                val execution = launch {
                    try {
                        val result = execute(taskId)
                        if (continuation.isActive) continuation.resume(result)
                    } catch (error: Throwable) {
                        if (continuation.isActive) continuation.resumeWithException(error)
                    }
                }
                continuation.invokeOnCancellation {
                    stop(taskId)
                    execution.cancel()
                }
            }
        }
}

interface DownloadWorkerExecutor {
    suspend fun execute(taskId: String): DownloadWorkerExecutionResult

    fun stop(taskId: String) = Unit
}

interface DownloadWorkerExecutorProvider {
    val downloadWorkerExecutor: DownloadWorkerExecutor?
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
