package dev.shallowdusty.oplusotastudio.download

import dev.shallowdusty.oplusotastudio.core.model.DownloadState
import dev.shallowdusty.oplusotastudio.core.model.DownloadTaskStore
import dev.shallowdusty.oplusotastudio.core.model.OtaPackage
import dev.shallowdusty.oplusotastudio.core.model.StoredDownloadTask
import java.io.File
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class WorkScheduledDownloadEngineTest {

    @Test
    fun `enqueue persists queued task and schedules work`() = runTest {
        val store = RecordingDownloadTaskStore()
        val scheduler = RecordingDownloadWorkScheduler()
        val engine = WorkScheduledDownloadEngine(
            taskStore = store,
            scheduler = scheduler,
            tempRoot = File("build/tmp/work-scheduled-download-engine/enqueue"),
            idGenerator = { "task-1" },
        )

        val task = engine.enqueue(samplePackage())

        assertEquals("task-1", task.taskId)
        assertEquals(listOf("task-1"), scheduler.scheduled)
        assertEquals("task-1", store.created.single().taskId)
        assertTrue(store.created.single().tempFilePath.endsWith("task-1.zip.part"))
        assertEquals(DownloadState.Queued, task.state.first())
    }

    @Test
    fun `observeAll exposes persisted tasks`() = runTest {
        val store = RecordingDownloadTaskStore(
            initialTasks = listOf(
                storedTask(
                    taskId = "task-1",
                    state = DownloadState.Running(
                        downloadedBytes = 5L,
                        targetSize = 10L,
                        speedBytesPerSec = null,
                    ),
                ),
            ),
        )
        val engine = WorkScheduledDownloadEngine(
            taskStore = store,
            scheduler = RecordingDownloadWorkScheduler(),
            tempRoot = File("build/tmp/work-scheduled-download-engine/observe"),
        )

        val task = engine.observeAll().first().single()

        assertEquals("task-1", task.taskId)
        assertEquals(
            DownloadState.Running(
                downloadedBytes = 5L,
                targetSize = 10L,
                speedBytesPerSec = null,
            ),
            task.state.first(),
        )
    }

    @Test
    fun `cancel deletes stored task and part file`() = runTest {
        val tempRoot = File("build/tmp/work-scheduled-download-engine/cancel")
        tempRoot.deleteRecursively()
        tempRoot.mkdirs()
        val partFile = tempRoot.resolve("task-1.zip.part").also { it.writeText("partial") }
        val store = RecordingDownloadTaskStore(
            initialTasks = listOf(
                storedTask(
                    taskId = "task-1",
                    state = DownloadState.Queued,
                    tempFilePath = partFile.path,
                ),
            ),
        )
        val scheduler = RecordingDownloadWorkScheduler()
        val engine = WorkScheduledDownloadEngine(
            taskStore = store,
            scheduler = scheduler,
            tempRoot = tempRoot,
        )
        val task = engine.observeAll().first().single()

        task.cancel()

        assertTrue(store.deleted.contains("task-1"))
        assertEquals(listOf("task-1"), scheduler.canceled)
        assertTrue(!partFile.exists())
    }

    @Test
    fun `resume schedules paused task again`() = runTest {
        val store = RecordingDownloadTaskStore(
            initialTasks = listOf(
                storedTask(
                    taskId = "task-1",
                    state = DownloadState.Paused(DownloadState.Paused.PauseReason.User),
                ),
            ),
        )
        val scheduler = RecordingDownloadWorkScheduler()
        val engine = WorkScheduledDownloadEngine(
            taskStore = store,
            scheduler = scheduler,
            tempRoot = File("build/tmp/work-scheduled-download-engine/resume"),
        )
        val task = engine.observeAll().first().single()

        task.resume()

        assertEquals(listOf("task-1"), scheduler.scheduled)
        assertEquals(DownloadState.Queued, task.state.first())
    }

    private fun samplePackage(): OtaPackage =
        OtaPackage(
            versionName = "test",
            type = "full",
            sizeBytes = 3L,
            sourceHost = "127.0.0.1",
            downloadUrl = "http://127.0.0.1/pkg.zip",
            md5 = null,
        )

    private fun storedTask(
        taskId: String,
        state: DownloadState,
        tempFilePath: String = "build/tmp/$taskId.zip.part",
    ): StoredDownloadTask =
        StoredDownloadTask(
            taskId = taskId,
            pkg = samplePackage(),
            tempFilePath = tempFilePath,
            finalFilePath = null,
            etag = null,
            lastModified = null,
            acceptRanges = false,
            state = state,
            updatedAtMs = 100L,
        )

    private class RecordingDownloadWorkScheduler : DownloadTaskWorkScheduler {
        val scheduled = mutableListOf<String>()
        val canceled = mutableListOf<String>()

        override suspend fun schedule(taskId: String) {
            scheduled += taskId
        }

        override fun cancel(taskId: String) {
            canceled += taskId
        }
    }

    private class RecordingDownloadTaskStore(
        initialTasks: List<StoredDownloadTask> = emptyList(),
    ) : DownloadTaskStore {
        private val tasks = MutableStateFlow(initialTasks)
        val created = mutableListOf<CreatedTask>()
        val deleted = mutableListOf<String>()

        override suspend fun createQueuedTask(
            taskId: String,
            pkg: OtaPackage,
            tempFilePath: String,
            updatedAtMs: Long,
        ) {
            created += CreatedTask(taskId, tempFilePath)
            tasks.value = tasks.value + StoredDownloadTask(
                taskId = taskId,
                pkg = pkg,
                tempFilePath = tempFilePath,
                finalFilePath = null,
                etag = null,
                lastModified = null,
                acceptRanges = false,
                state = DownloadState.Queued,
                updatedAtMs = updatedAtMs,
            )
        }

        override suspend fun updateState(
            taskId: String,
            state: DownloadState,
            updatedAtMs: Long,
        ) {
            tasks.value = tasks.value.map { task ->
                if (task.taskId == taskId) task.copy(state = state, updatedAtMs = updatedAtMs) else task
            }
        }

        override suspend fun updateResumeMetadata(
            taskId: String,
            etag: String?,
            lastModified: String?,
            acceptRanges: Boolean,
            updatedAtMs: Long,
        ) = Unit

        override suspend fun updateFinalFilePath(
            taskId: String,
            finalFilePath: String,
            updatedAtMs: Long,
        ) = Unit

        override suspend fun getTask(taskId: String): StoredDownloadTask? =
            tasks.value.singleOrNull { it.taskId == taskId }

        override suspend fun deleteTask(taskId: String) {
            deleted += taskId
            tasks.value = tasks.value.filterNot { it.taskId == taskId }
        }

        override fun observeTasks(): Flow<List<StoredDownloadTask>> = tasks
    }

    private data class CreatedTask(
        val taskId: String,
        val tempFilePath: String,
    )
}
