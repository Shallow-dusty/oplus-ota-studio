package dev.shallowdusty.oplusotastudio.feature.downloads

import androidx.annotation.StringRes
import dev.shallowdusty.oplusotastudio.core.model.DownloadState

data class DownloadStateText(
    @StringRes val resId: Int,
    val args: List<Any?> = emptyList(),
)

data class DownloadStatePresentation(
    val label: DownloadStateText,
    val details: List<DownloadStateText> = emptyList(),
    val rawDetails: String? = null,
    val progressFraction: Float? = null,
    val showIndeterminateProgress: Boolean = false,
    val speedBytesPerSec: Long? = null,
    val canPause: Boolean = false,
    val canResume: Boolean = false,
    val canCancel: Boolean = false,
)

fun DownloadState.toPresentation(): DownloadStatePresentation =
    when (this) {
        DownloadState.Queued -> DownloadStatePresentation(
            label = DownloadStateText(R.string.downloads_state_queued),
        )

        is DownloadState.Running -> DownloadStatePresentation(
            label = DownloadStateText(R.string.downloads_progress, listOf(downloadedBytes, targetSize)),
            progressFraction = targetSize
                ?.takeIf { it > 0L }
                ?.let { (downloadedBytes.toFloat() / it).coerceIn(0f, 1f) },
            speedBytesPerSec = speedBytesPerSec,
            canPause = true,
            canCancel = true,
        )

        is DownloadState.Paused -> DownloadStatePresentation(
            label = DownloadStateText(R.string.downloads_state_paused, listOf(reason.name.lowercase())),
            canResume = true,
            canCancel = true,
        )

        is DownloadState.Retrying -> DownloadStatePresentation(
            label = DownloadStateText(
                R.string.downloads_state_retrying,
                listOf(attempt, maxAttempts, category.name.lowercase()),
            ),
            showIndeterminateProgress = true,
        )

        DownloadState.Verifying -> DownloadStatePresentation(
            label = DownloadStateText(R.string.downloads_verifying),
            showIndeterminateProgress = true,
        )

        DownloadState.Verified -> DownloadStatePresentation(
            label = DownloadStateText(R.string.downloads_transfer_verified),
            details = listOf(DownloadStateText(R.string.downloads_transfer_verified_body)),
        )

        DownloadState.Unverified -> DownloadStatePresentation(
            label = DownloadStateText(R.string.downloads_unverified),
            details = listOf(DownloadStateText(R.string.downloads_unverified_body)),
        )

        DownloadState.Canceled -> DownloadStatePresentation(
            label = DownloadStateText(R.string.downloads_state_canceled),
        )

        is DownloadState.Failed -> DownloadStatePresentation(
            label = DownloadStateText(R.string.downloads_state_failed, listOf(category.name.lowercase())),
            details = buildList {
                if (retriesRemaining > 0) {
                    add(DownloadStateText(R.string.downloads_retries_remaining, listOf(retriesRemaining)))
                }
                expectedHash?.let { add(DownloadStateText(R.string.downloads_expected_hash, listOf(it))) }
                actualHash?.let { add(DownloadStateText(R.string.downloads_actual_hash, listOf(it))) }
            },
            rawDetails = raw,
        )
    }
