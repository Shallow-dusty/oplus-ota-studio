package dev.shallowdusty.oplusotastudio.core.storage

import dev.shallowdusty.oplusotastudio.core.model.DownloadState
import dev.shallowdusty.oplusotastudio.core.model.DownloadTaskStore
import dev.shallowdusty.oplusotastudio.core.model.OtaPackage
import dev.shallowdusty.oplusotastudio.core.model.StoredDownloadTask
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class RoomDownloadTaskStore(
    private val downloadTaskDao: DownloadTaskDao,
) : DownloadTaskStore {
    override suspend fun createQueuedTask(
        taskId: String,
        pkg: OtaPackage,
        tempFilePath: String,
        updatedAtMs: Long,
    ) {
        downloadTaskDao.upsert(
            DownloadTaskEntity.fromPackage(
                taskId = taskId,
                pkg = pkg,
                tempFilePath = tempFilePath,
                updatedAtMs = updatedAtMs,
            ),
        )
    }

    override suspend fun updateState(
        taskId: String,
        state: DownloadState,
        updatedAtMs: Long,
    ) {
        val current = downloadTaskDao.get(taskId) ?: return
        downloadTaskDao.upsert(current.withState(state, updatedAtMs))
    }

    override fun observeTasks(): Flow<List<StoredDownloadTask>> =
        downloadTaskDao.observeAll().map { rows ->
            rows.map { it.toStoredDownloadTask() }
        }
}

private fun DownloadTaskEntity.toStoredDownloadTask(): StoredDownloadTask =
    StoredDownloadTask(
        taskId = taskId,
        pkg = OtaPackage(
            versionName = versionName,
            type = packageType,
            sizeBytes = packageSize,
            sourceHost = sourceHost,
            downloadUrl = downloadUrl,
            md5 = md5,
            sha256 = sha256,
            releaseNotes = releaseNotes,
        ),
        tempFilePath = tempFilePath,
        finalFilePath = finalFilePath,
        state = toDownloadState(),
        updatedAtMs = updatedAtMs,
    )
