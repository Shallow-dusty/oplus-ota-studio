package dev.shallowdusty.oplusotastudio.core.storage

import dev.shallowdusty.oplusotastudio.core.model.DownloadState
import dev.shallowdusty.oplusotastudio.core.model.OtaErrorCategory

data class DownloadStateColumns(
    val state: String,
    val downloadedBytes: Long,
    val targetSize: Long?,
    val speedBytesPerSec: Long?,
    val pauseReason: String?,
    val retryAttempt: Int?,
    val maxRetryAttempts: Int?,
    val errorCategory: String?,
    val retriesRemaining: Int?,
    val rawError: String?,
    val expectedHash: String?,
    val actualHash: String?,
)

object DownloadStateStorageCodec {
    fun toColumns(state: DownloadState): DownloadStateColumns =
        when (state) {
            DownloadState.Queued -> base("Queued")
            is DownloadState.Running -> base(
                state = "Running",
                downloadedBytes = state.downloadedBytes,
                targetSize = state.targetSize,
                speedBytesPerSec = state.speedBytesPerSec,
            )
            is DownloadState.Paused -> base(
                state = "Paused",
                pauseReason = state.reason.name,
            )
            is DownloadState.Retrying -> base(
                state = "Retrying",
                retryAttempt = state.attempt,
                maxRetryAttempts = state.maxAttempts,
                errorCategory = state.category.name,
            )
            DownloadState.Verifying -> base("Verifying")
            DownloadState.Verified -> base("Verified")
            DownloadState.Unverified -> base("Unverified")
            DownloadState.Canceled -> base("Canceled")
            is DownloadState.Failed -> base(
                state = "Failed",
                errorCategory = state.category.name,
                retriesRemaining = state.retriesRemaining,
                rawError = state.raw,
                expectedHash = state.expectedHash,
                actualHash = state.actualHash,
            )
        }

    fun toDomain(columns: DownloadStateColumns): DownloadState =
        when (columns.state) {
            "Queued" -> DownloadState.Queued
            "Running" -> DownloadState.Running(
                downloadedBytes = columns.downloadedBytes,
                targetSize = columns.targetSize,
                speedBytesPerSec = columns.speedBytesPerSec,
            )
            "Paused" -> DownloadState.Paused(
                reason = enumValueOrDefault(
                    columns.pauseReason,
                    DownloadState.Paused.PauseReason.User,
                ),
            )
            "Retrying" -> DownloadState.Retrying(
                attempt = columns.retryAttempt ?: 1,
                maxAttempts = columns.maxRetryAttempts ?: 3,
                category = enumValueOrDefault(columns.errorCategory, OtaErrorCategory.Unknown),
            )
            "Verifying" -> DownloadState.Verifying
            "Verified" -> DownloadState.Verified
            "Unverified" -> DownloadState.Unverified
            "Canceled" -> DownloadState.Canceled
            "Failed" -> DownloadState.Failed(
                category = enumValueOrDefault(columns.errorCategory, OtaErrorCategory.Unknown),
                retriesRemaining = columns.retriesRemaining ?: 0,
                raw = columns.rawError,
                expectedHash = columns.expectedHash,
                actualHash = columns.actualHash,
            )
            else -> DownloadState.Failed(
                category = OtaErrorCategory.Unknown,
                retriesRemaining = 0,
                raw = "Unknown stored download state: ${columns.state}",
            )
        }

    private fun base(
        state: String,
        downloadedBytes: Long = 0L,
        targetSize: Long? = null,
        speedBytesPerSec: Long? = null,
        pauseReason: String? = null,
        retryAttempt: Int? = null,
        maxRetryAttempts: Int? = null,
        errorCategory: String? = null,
        retriesRemaining: Int? = null,
        rawError: String? = null,
        expectedHash: String? = null,
        actualHash: String? = null,
    ): DownloadStateColumns =
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

    private inline fun <reified T : Enum<T>> enumValueOrDefault(raw: String?, default: T): T =
        raw?.let { runCatching { enumValueOf<T>(it) }.getOrNull() } ?: default
}
