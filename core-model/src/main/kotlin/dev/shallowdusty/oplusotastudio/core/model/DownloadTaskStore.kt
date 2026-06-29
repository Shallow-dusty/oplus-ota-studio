package dev.shallowdusty.oplusotastudio.core.model

import kotlinx.coroutines.flow.Flow

data class StoredDownloadTask(
    val taskId: String,
    val pkg: OtaPackage,
    val tempFilePath: String,
    val finalFilePath: String?,
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

    fun observeTasks(): Flow<List<StoredDownloadTask>>
}
