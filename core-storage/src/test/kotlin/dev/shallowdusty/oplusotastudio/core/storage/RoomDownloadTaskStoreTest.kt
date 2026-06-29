package dev.shallowdusty.oplusotastudio.core.storage

import dev.shallowdusty.oplusotastudio.core.model.DownloadState
import dev.shallowdusty.oplusotastudio.core.model.OtaErrorCategory
import dev.shallowdusty.oplusotastudio.core.model.OtaPackage
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class RoomDownloadTaskStoreTest {

    @Test
    fun `createQueuedTask persists package metadata and queued state`() = runTest {
        val dao = FakeDownloadTaskDao()
        val store = RoomDownloadTaskStore(dao)

        store.createQueuedTask(
            taskId = "task-1",
            pkg = samplePackage(),
            tempFilePath = "/cache/task-1.zip.part",
            updatedAtMs = 100L,
        )

        val saved = dao.upserts.single()
        assertEquals("task-1", saved.taskId)
        assertEquals("https://otagm.oppo.com/package.zip", saved.downloadUrl)
        assertEquals("Queued", saved.state)
        assertEquals(100L, saved.updatedAtMs)
    }

    @Test
    fun `updateState rewrites only stored state columns`() = runTest {
        val dao = FakeDownloadTaskDao()
        val store = RoomDownloadTaskStore(dao)
        dao.rows.value = listOf(
            DownloadTaskEntity.fromPackage(
                taskId = "task-1",
                pkg = samplePackage(),
                tempFilePath = "/cache/task-1.zip.part",
                updatedAtMs = 100L,
            ),
        )

        store.updateState(
            taskId = "task-1",
            state = DownloadState.Failed(
                category = OtaErrorCategory.Network,
                retriesRemaining = 1,
                raw = "timeout",
            ),
            updatedAtMs = 200L,
        )

        val saved = dao.upserts.single()
        assertEquals("task-1", saved.taskId)
        assertEquals("Failed", saved.state)
        assertEquals("Network", saved.errorCategory)
        assertEquals(1, saved.retriesRemaining)
        assertEquals("timeout", saved.rawError)
        assertEquals(200L, saved.updatedAtMs)
    }

    @Test
    fun `updateResumeMetadata rewrites validator fields and preserves state`() = runTest {
        val dao = FakeDownloadTaskDao()
        val store = RoomDownloadTaskStore(dao)
        dao.rows.value = listOf(
            DownloadTaskEntity.fromPackage(
                taskId = "task-1",
                pkg = samplePackage(),
                tempFilePath = "/cache/task-1.zip.part",
                updatedAtMs = 100L,
            ).withState(
                state = DownloadState.Running(128L, 1024L, 64L),
                updatedAtMs = 150L,
            ),
        )

        store.updateResumeMetadata(
            taskId = "task-1",
            etag = "\"abc\"",
            lastModified = "Tue, 30 Jun 2026 00:00:00 GMT",
            acceptRanges = true,
            updatedAtMs = 200L,
        )

        val saved = dao.upserts.single()
        assertEquals("\"abc\"", saved.etag)
        assertEquals("Tue, 30 Jun 2026 00:00:00 GMT", saved.lastModified)
        assertEquals(true, saved.acceptRanges)
        assertEquals("Running", saved.state)
        assertEquals(128L, saved.downloadedBytes)
        assertEquals(200L, saved.updatedAtMs)
    }

    @Test
    fun `updateFinalFilePath stores promoted file path and preserves verified state`() = runTest {
        val dao = FakeDownloadTaskDao()
        val store = RoomDownloadTaskStore(dao)
        dao.rows.value = listOf(
            DownloadTaskEntity.fromPackage(
                taskId = "task-1",
                pkg = samplePackage(),
                tempFilePath = "/cache/task-1.zip.part",
                updatedAtMs = 100L,
            ).withState(
                state = DownloadState.Verified,
                updatedAtMs = 150L,
            ),
        )

        store.updateFinalFilePath(
            taskId = "task-1",
            finalFilePath = "content://media/external/downloads/42",
            updatedAtMs = 200L,
        )

        val saved = dao.upserts.single()
        assertEquals("content://media/external/downloads/42", saved.finalFilePath)
        assertEquals("Verified", saved.state)
        assertEquals(200L, saved.updatedAtMs)
    }

    @Test
    fun `observeTasks maps entities to stored download tasks`() = runTest {
        val dao = FakeDownloadTaskDao()
        val store = RoomDownloadTaskStore(dao)
        dao.rows.value = listOf(
            DownloadTaskEntity.fromPackage(
                taskId = "task-1",
                pkg = samplePackage(),
                tempFilePath = "/cache/task-1.zip.part",
                updatedAtMs = 100L,
            ).withState(
                state = DownloadState.Running(128L, 1024L, 64L),
                updatedAtMs = 200L,
            ),
        )

        val tasks = store.observeTasks().first()

        assertEquals("task-1", tasks.single().taskId)
        assertEquals("14.0.0.1901", tasks.single().pkg.versionName)
        assertEquals("/cache/task-1.zip.part", tasks.single().tempFilePath)
        assertEquals(DownloadState.Running(128L, 1024L, 64L), tasks.single().state)
        assertEquals(200L, tasks.single().updatedAtMs)
    }

    @Test
    fun `getTask maps resume metadata to stored download task`() = runTest {
        val dao = FakeDownloadTaskDao()
        val store = RoomDownloadTaskStore(dao)
        dao.rows.value = listOf(
            DownloadTaskEntity.fromPackage(
                taskId = "task-1",
                pkg = samplePackage(),
                tempFilePath = "/cache/task-1.zip.part",
                updatedAtMs = 100L,
            ).copy(
                etag = "\"abc\"",
                lastModified = "Tue, 30 Jun 2026 00:00:00 GMT",
                acceptRanges = true,
            ),
        )

        val task = store.getTask("task-1")

        assertEquals("\"abc\"", task?.etag)
        assertEquals("Tue, 30 Jun 2026 00:00:00 GMT", task?.lastModified)
        assertEquals(true, task?.acceptRanges)
    }

    @Test
    fun `deleteTask removes stored task row`() = runTest {
        val dao = FakeDownloadTaskDao()
        val store = RoomDownloadTaskStore(dao)
        dao.rows.value = listOf(
            DownloadTaskEntity.fromPackage(
                taskId = "task-1",
                pkg = samplePackage(),
                tempFilePath = "/cache/task-1.zip.part",
                updatedAtMs = 100L,
            ),
        )

        store.deleteTask("task-1")

        assertEquals(emptyList<DownloadTaskEntity>(), dao.rows.value)
    }

    private fun samplePackage(): OtaPackage =
        OtaPackage(
            versionName = "14.0.0.1901",
            type = "full",
            sizeBytes = 6_559_817_109L,
            sourceHost = "otagm.oppo.com",
            downloadUrl = "https://otagm.oppo.com/package.zip",
            md5 = "md5",
            sha256 = "sha256",
            releaseNotes = "notes",
        )

    private class FakeDownloadTaskDao : DownloadTaskDao {
        val rows = MutableStateFlow<List<DownloadTaskEntity>>(emptyList())
        val upserts = mutableListOf<DownloadTaskEntity>()

        override suspend fun upsert(task: DownloadTaskEntity) {
            upserts += task
            rows.value = rows.value.filterNot { it.taskId == task.taskId } + task
        }

        override fun observeAll(): Flow<List<DownloadTaskEntity>> = rows

        override suspend fun get(taskId: String): DownloadTaskEntity? =
            rows.value.firstOrNull { it.taskId == taskId }

        override suspend fun delete(taskId: String) {
            rows.value = rows.value.filterNot { it.taskId == taskId }
        }
    }
}
