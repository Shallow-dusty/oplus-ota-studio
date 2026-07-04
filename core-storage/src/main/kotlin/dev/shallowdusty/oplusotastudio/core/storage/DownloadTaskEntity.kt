package dev.shallowdusty.oplusotastudio.core.storage

import androidx.room.Entity
import androidx.room.PrimaryKey
import dev.shallowdusty.oplusotastudio.core.model.DownloadState
import dev.shallowdusty.oplusotastudio.core.model.OtaPackage

@Entity(tableName = "download_tasks")
data class DownloadTaskEntity(
    @PrimaryKey val taskId: String,
    val downloadUrl: String,
    val sourceHost: String,
    val versionName: String,
    val packageType: String,
    val packageSize: Long,
    val md5: String?,
    val sha256: String?,
    val releaseNotes: String?,
    val tempFilePath: String,
    val finalFilePath: String?,
    val etag: String?,
    val lastModified: String?,
    val acceptRanges: Boolean,
    val downloadedBytes: Long,
    val targetSize: Long?,
    val speedBytesPerSec: Long?,
    val state: String,
    val pauseReason: String?,
    val retryAttempt: Int?,
    val maxRetryAttempts: Int?,
    val errorCategory: String?,
    val retriesRemaining: Int?,
    val rawError: String?,
    val expectedHash: String?,
    val actualHash: String?,
    val updatedAtMs: Long,
) {
    fun toDownloadState(): DownloadState =
        DownloadStateStorageCodec.toDomain(toStateColumns())

    fun withState(state: DownloadState, updatedAtMs: Long): DownloadTaskEntity {
        val columns = DownloadStateStorageCodec.toColumns(state)
        return copy(
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
        )
    }

    private fun toStateColumns(): DownloadStateColumns =
        DownloadStateColumns(
            state = state,
            downloadedBytes = downloadedBytes,
            targetSize = targetSize,
            speedBytesPerSec = speedBytesPerSec,
            pauseReason = pauseReason,
            retryAttempt = retryAttempt,
            maxRetryAttempts = maxRetryAttempts,
            errorCategory = errorCategory,
            retriesRemaining = retriesRemaining,
            rawError = rawError,
            expectedHash = expectedHash,
            actualHash = actualHash,
        )

    companion object {
        fun fromPackage(
            taskId: String,
            pkg: OtaPackage,
            tempFilePath: String,
            updatedAtMs: Long,
        ): DownloadTaskEntity {
            val columns = DownloadStateStorageCodec.toColumns(DownloadState.Queued)
            return DownloadTaskEntity(
                taskId = taskId,
                downloadUrl = pkg.downloadUrl,
                sourceHost = pkg.sourceHost,
                versionName = pkg.versionName,
                packageType = pkg.type ?: "unknown",
                packageSize = pkg.sizeBytes,
                md5 = pkg.md5,
                sha256 = pkg.sha256,
                releaseNotes = pkg.releaseNotes,
                tempFilePath = tempFilePath,
                finalFilePath = null,
                etag = null,
                lastModified = null,
                acceptRanges = false,
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
            )
        }
    }
}
