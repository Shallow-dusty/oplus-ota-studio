package dev.shallowdusty.oplusotastudio.core.model

import kotlinx.coroutines.flow.Flow

data class StoredDownloadTask(
    val taskId: String,
    val pkg: OtaPackage,
    val tempFilePath: String,
    val finalFilePath: String?,
    val etag: String?,
    val lastModified: String?,
    val acceptRanges: Boolean,
    val state: DownloadState,
    val updatedAtMs: Long,
)

interface DownloadTaskStore {
    suspend fun createQueuedTask(
        taskId: String,
        pkg: OtaPackage,
        tempFilePath: String,
        updatedAtMs: Long,
    )

    suspend fun updateState(
        taskId: String,
        state: DownloadState,
        updatedAtMs: Long,
    )

    suspend fun updateResumeMetadata(
        taskId: String,
        etag: String?,
        lastModified: String?,
        acceptRanges: Boolean,
        updatedAtMs: Long,
    )

    suspend fun getTask(taskId: String): StoredDownloadTask?

    fun observeTasks(): Flow<List<StoredDownloadTask>>
}
