package dev.shallowdusty.oplusotastudio.download

import dev.shallowdusty.oplusotastudio.core.model.DownloadEngine
import dev.shallowdusty.oplusotastudio.core.model.DownloadState
import dev.shallowdusty.oplusotastudio.core.model.DownloadTask
import dev.shallowdusty.oplusotastudio.core.model.DownloadTaskStore
import dev.shallowdusty.oplusotastudio.core.model.OtaPackage
import java.io.File
import java.util.UUID
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class WorkScheduledDownloadEngine(
    private val taskStore: DownloadTaskStore,
    private val scheduler: DownloadTaskWorkScheduler,
    private val tempRoot: File,
    private val idGenerator: () -> String = { UUID.randomUUID().toString() },
    private val nowMs: () -> Long = { System.currentTimeMillis() },
) : DownloadEngine {

    override suspend fun enqueue(pkg: OtaPackage): DownloadTask {
        tempRoot.mkdirs()
        val taskId = idGenerator()
        val tempFile = tempRoot.resolve("$taskId.zip.part")
        taskStore.createQueuedTask(
            taskId = taskId,
            pkg = pkg,
            tempFilePath = tempFile.path,
            updatedAtMs = nowMs(),
        )
        scheduler.schedule(taskId)
        return storedTaskHandle(taskId, tempFile)
    }

    override fun observeAll(): Flow<List<DownloadTask>> =
        taskStore.observeTasks().map { tasks ->
            tasks.map { task -> storedTaskHandle(task.taskId, File(task.tempFilePath)) }
        }

    private fun storedTaskHandle(
        taskId: String,
        tempFile: File,
    ): DownloadTask =
        object : DownloadTask {
            override val taskId: String = taskId

            override val state: Flow<DownloadState> =
                taskStore.observeTasks().map { tasks ->
                    tasks.singleOrNull { it.taskId == taskId }?.state ?: DownloadState.Canceled
                }

            override suspend fun pause() {
                if (currentState()?.isTerminal == true) return
                scheduler.cancel(taskId)
                taskStore.updateState(
                    taskId = taskId,
                    state = DownloadState.Paused(DownloadState.Paused.PauseReason.User),
                    updatedAtMs = nowMs(),
                )
            }

            override suspend fun resume() {
                if (currentState()?.isTerminal == true) return
                taskStore.updateState(
                    taskId = taskId,
                    state = DownloadState.Queued,
                    updatedAtMs = nowMs(),
                )
                scheduler.schedule(taskId)
            }

            override suspend fun cancel() {
                if (currentState()?.isTerminal == true) return
                scheduler.cancel(taskId)
                tempFile.delete()
                taskStore.deleteTask(taskId)
            }

            private suspend fun currentState(): DownloadState? =
                taskStore.getTask(taskId)?.state
        }
}

private val DownloadState.isTerminal: Boolean
    get() =
        when (this) {
            DownloadState.Verified,
            DownloadState.Canceled -> true
            is DownloadState.Failed -> retriesRemaining <= 0
            else -> false
        }
