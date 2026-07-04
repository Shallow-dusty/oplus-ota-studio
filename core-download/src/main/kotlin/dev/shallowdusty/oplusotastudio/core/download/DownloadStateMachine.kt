package dev.shallowdusty.oplusotastudio.core.download

import dev.shallowdusty.oplusotastudio.core.model.DownloadState
import dev.shallowdusty.oplusotastudio.core.model.OtaErrorCategory
import dev.shallowdusty.oplusotastudio.core.model.isRetriable

class DownloadStateMachine {

    fun start(
        current: DownloadState,
        downloadedBytes: Long,
        targetSize: Long?,
        speedBytesPerSec: Long?,
    ): DownloadState =
        when (current) {
            DownloadState.Queued,
            is DownloadState.Retrying -> DownloadState.Running(
                downloadedBytes = downloadedBytes,
                targetSize = targetSize,
                speedBytesPerSec = speedBytesPerSec,
            )
            else -> current
        }

    fun pause(
        current: DownloadState,
        reason: DownloadState.Paused.PauseReason,
    ): DownloadState =
        when (current) {
            is DownloadState.Running -> DownloadState.Paused(reason)
            else -> current
        }

    fun resume(
        current: DownloadState,
        downloadedBytes: Long,
        targetSize: Long?,
        speedBytesPerSec: Long?,
    ): DownloadState =
        when (current) {
            is DownloadState.Paused -> DownloadState.Running(
                downloadedBytes = downloadedBytes,
                targetSize = targetSize,
                speedBytesPerSec = speedBytesPerSec,
            )
            else -> current
        }

    fun complete(current: DownloadState): DownloadState =
        when (current) {
            is DownloadState.Running -> DownloadState.Verifying
            else -> current
        }

    fun verificationSucceeded(current: DownloadState): DownloadState =
        when (current) {
            DownloadState.Verifying -> DownloadState.Verified
            else -> current
        }

    fun verificationUnavailable(current: DownloadState): DownloadState =
        when (current) {
            DownloadState.Verifying -> DownloadState.Unverified
            else -> current
        }

    fun verificationFailed(current: DownloadState, raw: String?): DownloadState =
        when (current) {
            DownloadState.Verifying -> DownloadState.Failed(
                category = OtaErrorCategory.ChecksumMismatch,
                retriesRemaining = 0,
                raw = raw,
            )
            else -> current
        }

    fun fail(
        current: DownloadState,
        category: OtaErrorCategory,
        retriesRemaining: Int,
        raw: String?,
    ): DownloadState =
        when (current) {
            is DownloadState.Running -> DownloadState.Failed(
                category = category,
                retriesRemaining = retriesRemaining.coerceAtLeast(0),
                raw = raw,
            )
            else -> current
        }

    fun retry(
        current: DownloadState,
        attempt: Int,
        maxAttempts: Int,
    ): DownloadState =
        when {
            current is DownloadState.Failed &&
                current.category.isRetriable &&
                current.retriesRemaining > 0 -> DownloadState.Retrying(
                    attempt = attempt,
                    maxAttempts = maxAttempts,
                    category = current.category,
                )
            else -> current
        }

    fun cancel(current: DownloadState): DownloadState =
        when (current) {
            DownloadState.Queued,
            is DownloadState.Running,
            is DownloadState.Paused -> DownloadState.Canceled
            else -> current
        }
}
