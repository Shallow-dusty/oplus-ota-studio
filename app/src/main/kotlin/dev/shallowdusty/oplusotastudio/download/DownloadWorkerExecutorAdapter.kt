package dev.shallowdusty.oplusotastudio.download

import dev.shallowdusty.oplusotastudio.core.model.DownloadState
import java.io.IOException
import kotlinx.coroutines.CancellationException

class DownloadWorkerExecutorAdapter(
    private val executeStoredTask: suspend (String) -> DownloadState,
) : DownloadWorkerExecutor {

    override suspend fun execute(taskId: String): DownloadWorkerExecutionResult =
        try {
            DownloadWorkerExecutionMapper.fromState(executeStoredTask(taskId))
        } catch (error: CancellationException) {
            throw error
        } catch (error: IOException) {
            DownloadWorkerExecutionResult.Retry
        } catch (error: Throwable) {
            DownloadWorkerExecutionResult.Failed
        }
}

object DownloadWorkerExecutionMapper {
    fun fromState(state: DownloadState): DownloadWorkerExecutionResult =
        when (state) {
            DownloadState.Verified -> DownloadWorkerExecutionResult.Succeeded
            is DownloadState.Retrying -> DownloadWorkerExecutionResult.Retry
            else -> DownloadWorkerExecutionResult.Failed
        }
}
