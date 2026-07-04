package dev.shallowdusty.oplusotastudio.download

import dev.shallowdusty.oplusotastudio.core.download.DownloadAdmissionGate
import dev.shallowdusty.oplusotastudio.core.model.DownloadEngine
import dev.shallowdusty.oplusotastudio.core.model.DownloadState
import dev.shallowdusty.oplusotastudio.core.model.DownloadTask
import dev.shallowdusty.oplusotastudio.core.model.DownloadTaskStore
import dev.shallowdusty.oplusotastudio.core.model.OtaErrorCategory
import dev.shallowdusty.oplusotastudio.core.model.OtaPackage
import dev.shallowdusty.oplusotastudio.core.model.StoredDownloadTask
import dev.shallowdusty.oplusotastudio.core.model.isRetriable
import java.io.File
import java.util.UUID
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

class WorkScheduledDownloadEngine(
    private val taskStore: DownloadTaskStore,
    private val scheduler: DownloadTaskWorkScheduler,
    private val tempRoot: File,
    private val idGenerator: () -> String = { UUID.randomUUID().toString() },
    private val nowMs: () -> Long = { System.currentTimeMillis() },
    private val maxQueuedTasks: Int = DefaultMaxQueuedTasks,
    private val admissionGate: DownloadAdmissionGate = DownloadAdmissionGate.AllowAll,
) : DownloadEngine {

    override suspend fun enqueue(pkg: OtaPackage): DownloadTask {
        tempRoot.mkdirs()
        val taskId = idGenerator()
        val tempFile = tempRoot.resolve("$taskId.zip.part")
        val activeTasks = taskStore.observeTasks()
            .first()
            .count { task -> !task.state.isTerminal }
        if (activeTasks >= maxQueuedTasks) {
            return rejectAndPersist(
                taskId = taskId,
                pkg = pkg,
                tempFile = tempFile,
                raw = "Download queue limit reached ($maxQueuedTasks tasks)",
            )
        }
        admissionGate.rejectionReason()?.let { reason ->
            val pauseReason = admissionGate.rejectionPauseReason()
            return if (pauseReason != null) {
                pauseAndPersist(
                    taskId = taskId,
                    pkg = pkg,
                    tempFile = tempFile,
                    reason = pauseReason,
                )
            } else {
                rejectAndPersist(
                    taskId = taskId,
                    pkg = pkg,
                    tempFile = tempFile,
                    raw = reason,
                )
            }
        }
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

    suspend fun rescheduleRecoverableTasks() {
        taskStore.observeTasks()
            .first()
            .filter { task -> task.shouldRecover }
            .forEach { task ->
                if (task.state != DownloadState.Queued) {
                    taskStore.updateState(
                        taskId = task.taskId,
                        state = DownloadState.Queued,
                        updatedAtMs = nowMs(),
                    )
                }
                scheduler.recover(task.taskId)
            }
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
                if (currentState()?.isTerminal == true) return
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

    private suspend fun pauseAndPersist(
        taskId: String,
        pkg: OtaPackage,
        tempFile: File,
        reason: DownloadState.Paused.PauseReason,
    ): DownloadTask {
        taskStore.createQueuedTask(
            taskId = taskId,
            pkg = pkg,
            tempFilePath = tempFile.path,
            updatedAtMs = nowMs(),
        )
        taskStore.updateState(
            taskId = taskId,
            state = DownloadState.Paused(reason),
            updatedAtMs = nowMs(),
        )
        scheduler.schedule(taskId)
        return storedTaskHandle(taskId, tempFile)
    }

    private suspend fun rejectAndPersist(
        taskId: String,
        pkg: OtaPackage,
        tempFile: File,
        raw: String,
    ): DownloadTask {
        val failed = DownloadState.Failed(
            category = OtaErrorCategory.File,
            retriesRemaining = 0,
            raw = raw,
        )
        taskStore.createQueuedTask(
            taskId = taskId,
            pkg = pkg,
            tempFilePath = tempFile.path,
            updatedAtMs = nowMs(),
        )
        taskStore.updateState(
            taskId = taskId,
            state = failed,
            updatedAtMs = nowMs(),
        )
        return storedTaskHandle(taskId, tempFile)
    }

    private companion object {
        const val DefaultMaxQueuedTasks = 20
    }
}

private val StoredDownloadTask.shouldRecover: Boolean
    get() =
        when (val current = state) {
            DownloadState.Queued,
            is DownloadState.Running,
            is DownloadState.Retrying,
            DownloadState.Verifying,
            -> true
            is DownloadState.Paused -> current.reason != DownloadState.Paused.PauseReason.User
            is DownloadState.Failed -> current.category.isRetriable && current.retriesRemaining > 0
            DownloadState.Verified,
            DownloadState.Unverified,
            DownloadState.Canceled,
            -> false
}

private val DownloadState.isTerminal: Boolean
    get() =
        when (this) {
            DownloadState.Verified,
            DownloadState.Unverified,
            DownloadState.Canceled -> true
            is DownloadState.Failed -> retriesRemaining <= 0
            else -> false
        }
