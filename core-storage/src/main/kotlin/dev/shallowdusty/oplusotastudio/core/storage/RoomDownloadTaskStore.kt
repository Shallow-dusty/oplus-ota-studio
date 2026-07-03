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
        val columns = DownloadStateStorageCodec.toColumns(state)
        downloadTaskDao.updateStateColumns(
            taskId = taskId,
            downloadedBytes = columns.downloadedBytes,
            targetSize = columns.targetSize,
            speedBytesPerSec = columns.speedBytesPerSec,
            state = columns.state,
            pauseReason = columns.pauseReason,
            retryAttempt = columns.retryAttempt,
            maxRetryAttempts = columns.maxRetryAttempts,
            errorCategory = columns.errorCategory,
            retriesRemaining = columns.retriesRemaining,
            rawError = columns.rawError,
            expectedHash = columns.expectedHash,
            actualHash = columns.actualHash,
            updatedAtMs = updatedAtMs,
            canOverrideUserPause = state.canOverrideUserPause(),
        )
    }

    override suspend fun updateResumeMetadata(
        taskId: String,
        etag: String?,
        lastModified: String?,
        acceptRanges: Boolean,
        updatedAtMs: Long,
    ) {
        val current = downloadTaskDao.get(taskId) ?: return
        downloadTaskDao.upsert(
            current.copy(
                etag = etag,
                lastModified = lastModified,
                acceptRanges = acceptRanges,
                updatedAtMs = updatedAtMs,
            ),
        )
    }

    override suspend fun updateFinalFilePath(
        taskId: String,
        finalFilePath: String,
        updatedAtMs: Long,
    ) {
        val current = downloadTaskDao.get(taskId) ?: return
        downloadTaskDao.upsert(
            current.copy(
                finalFilePath = finalFilePath,
                updatedAtMs = updatedAtMs,
            ),
        )
    }

    override suspend fun getTask(taskId: String): StoredDownloadTask? =
        downloadTaskDao.get(taskId)?.toStoredDownloadTask()

    override suspend fun deleteTask(taskId: String) {
        downloadTaskDao.delete(taskId)
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
        etag = etag,
        lastModified = lastModified,
        acceptRanges = acceptRanges,
        state = toDownloadState(),
        updatedAtMs = updatedAtMs,
    )

private fun DownloadState.canOverrideUserPause(): Boolean =
    when (this) {
        DownloadState.Canceled,
        DownloadState.Queued,
        is DownloadState.Paused -> true
        else -> false
    }
