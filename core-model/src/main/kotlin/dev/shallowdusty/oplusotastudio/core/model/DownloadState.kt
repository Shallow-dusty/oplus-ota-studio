package dev.shallowdusty.oplusotastudio.core.model

/**
 * Download task lifecycle state (spec §3.5 state machine).
 *
 * Terminal states: [Verified], [Unverified], [Canceled], and [Failed] once retries are
 * exhausted. The transition logic itself lives in core-download
 * (DownloadStateMachine); this type only enumerates the states and the data
 * each carries so the UI can render without depending on the engine.
 *
 * Every transition persists to Room before notifying the UI (spec §3.5), so the
 * state observed here is always recoverable across process death.
 */
sealed interface DownloadState {

    /** Enqueued, waiting for the single-task slot (spec §3.1 serial queue). */
    data object Queued : DownloadState

    /** Actively downloading. */
    data class Running(
        val downloadedBytes: Long,
        val targetSize: Long?,
        /** bytes/sec, for speed display; null until measured. */
        val speedBytesPerSec: Long?,
    ) : DownloadState

    /** User-paused or paused by network loss / low battery. */
    data class Paused(val reason: PauseReason) : DownloadState {
        enum class PauseReason { User, NetworkLost, MeteredNetwork, BatteryLow }
    }

    /** Backoff between retry attempts for a retriable error. */
    data class Retrying(
        val attempt: Int,
        val maxAttempts: Int,
        val category: OtaErrorCategory,
    ) : DownloadState

    /** Download complete, checksum being verified (spec §4). */
    data object Verifying : DownloadState

    /** Checksum passed. Terminal. */
    data object Verified : DownloadState

    /** Download completed but no checksum was available, so transfer integrity is unknown. Terminal. */
    data object Unverified : DownloadState

    /** User canceled. Terminal; .part + Room row cleaned up on next idle tick. */
    data object Canceled : DownloadState

    /**
     * Failed. Terminal once [retriesRemaining] == 0; otherwise the engine
     * transitions to [Retrying]. [category] drives retry policy (spec §3.5):
     * ChecksumMismatch is never retried automatically.
     */
    data class Failed(
        val category: OtaErrorCategory,
        val retriesRemaining: Int,
        val raw: String?,
        val expectedHash: String? = null,
        val actualHash: String? = null,
    ) : DownloadState
}
