package dev.shallowdusty.oplusotastudio.core.storage

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface DownloadTaskDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(task: DownloadTaskEntity)

    @Query("SELECT * FROM download_tasks ORDER BY updatedAtMs DESC")
    fun observeAll(): Flow<List<DownloadTaskEntity>>

    @Query("SELECT * FROM download_tasks WHERE taskId = :taskId LIMIT 1")
    suspend fun get(taskId: String): DownloadTaskEntity?

    @Query(
        """
        UPDATE download_tasks
        SET
            downloadedBytes = :downloadedBytes,
            targetSize = :targetSize,
            speedBytesPerSec = :speedBytesPerSec,
            state = :state,
            pauseReason = :pauseReason,
            retryAttempt = :retryAttempt,
            maxRetryAttempts = :maxRetryAttempts,
            errorCategory = :errorCategory,
            retriesRemaining = :retriesRemaining,
            rawError = :rawError,
            expectedHash = :expectedHash,
            actualHash = :actualHash,
            updatedAtMs = :updatedAtMs
        WHERE taskId = :taskId
            AND (
                :canOverrideUserPause = 1
                OR state != 'Paused'
                OR pauseReason != 'User'
            )
        """,
    )
    suspend fun updateStateColumns(
        taskId: String,
        downloadedBytes: Long,
        targetSize: Long?,
        speedBytesPerSec: Long?,
        state: String,
        pauseReason: String?,
        retryAttempt: Int?,
        maxRetryAttempts: Int?,
        errorCategory: String?,
        retriesRemaining: Int?,
        rawError: String?,
        expectedHash: String?,
        actualHash: String?,
        updatedAtMs: Long,
        canOverrideUserPause: Boolean,
    ): Int

    @Query(
        """
        UPDATE download_tasks
        SET
            etag = :etag,
            lastModified = :lastModified,
            acceptRanges = :acceptRanges,
            updatedAtMs = :updatedAtMs
        WHERE taskId = :taskId
        """,
    )
    suspend fun updateResumeMetadataColumns(
        taskId: String,
        etag: String?,
        lastModified: String?,
        acceptRanges: Boolean,
        updatedAtMs: Long,
    ): Int

    @Query(
        """
        UPDATE download_tasks
        SET
            finalFilePath = :finalFilePath,
            updatedAtMs = :updatedAtMs
        WHERE taskId = :taskId
        """,
    )
    suspend fun updateFinalFilePathColumn(
        taskId: String,
        finalFilePath: String,
        updatedAtMs: Long,
    ): Int

    @Query("DELETE FROM download_tasks WHERE taskId = :taskId")
    suspend fun delete(taskId: String)
}
