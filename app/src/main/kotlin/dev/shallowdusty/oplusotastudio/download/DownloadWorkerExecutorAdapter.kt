package dev.shallowdusty.oplusotastudio.download

import dev.shallowdusty.oplusotastudio.core.model.DownloadState

class DownloadWorkerExecutorAdapter(
    private val executeStoredTask: suspend (String) -> DownloadState,
) : DownloadWorkerExecutor {

    override suspend fun execute(taskId: String): DownloadWorkerExecutionResult =
        DownloadWorkerExecutionMapper.fromState(executeStoredTask(taskId))
}

object DownloadWorkerExecutionMapper {
    fun fromState(state: DownloadState): DownloadWorkerExecutionResult =
        when (state) {
            DownloadState.Verified -> DownloadWorkerExecutionResult.Succeeded
            is DownloadState.Retrying -> DownloadWorkerExecutionResult.Retry
            else -> DownloadWorkerExecutionResult.Failed
        }
}
