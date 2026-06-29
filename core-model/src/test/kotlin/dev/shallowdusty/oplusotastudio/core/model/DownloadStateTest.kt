package dev.shallowdusty.oplusotastudio.core.model

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class DownloadStateTest {

    @Test
    fun `Running carries progress, target, and speed`() {
        val state = DownloadState.Running(
            downloadedBytes = 1_000_000L,
            targetSize = 3_500_000_000L,
            speedBytesPerSec = 5_000_000L,
        )
        assertEquals(1_000_000L, state.downloadedBytes)
        assertEquals(3_500_000_000L, state.targetSize)
        assertEquals(5_000_000L, state.speedBytesPerSec)
    }

    @Test
    fun `Running speed is nullable until measured`() {
        // Spec §7: speed is derived; before the first measurement window it is null.
        val state = DownloadState.Running(0L, null, null)
        assertNullState(state)
    }

    @Test
    fun `Paused encodes its reason`() {
        // Spec §3.4: pause can be user-initiated or system-initiated.
        assertEquals(
            DownloadState.Paused.PauseReason.NetworkLost,
            DownloadState.Paused(DownloadState.Paused.PauseReason.NetworkLost).reason,
        )
        assertEquals(
            DownloadState.Paused.PauseReason.MeteredNetwork,
            DownloadState.Paused(DownloadState.Paused.PauseReason.MeteredNetwork).reason,
        )
    }

    @Test
    fun `Retrying carries attempt budget and category`() {
        val state = DownloadState.Retrying(
            attempt = 2,
            maxAttempts = 3,
            category = OtaErrorCategory.Network,
        )
        assertEquals(2, state.attempt)
        assertEquals(3, state.maxAttempts)
        assertEquals(OtaErrorCategory.Network, state.category)
    }

    @Test
    fun `Failed carries category, remaining retries, and raw`() {
        val state = DownloadState.Failed(
            category = OtaErrorCategory.ChecksumMismatch,
            retriesRemaining = 0,
            raw = "expected abc, got def",
        )
        assertEquals(OtaErrorCategory.ChecksumMismatch, state.category)
        assertEquals(0, state.retriesRemaining)
        assertEquals("expected abc, got def", state.raw)
    }

    @Test
    fun `Queued, Verifying, Verified, Canceled are object singletons`() {
        // These states carry no data; identity equality holds.
        assertEquals(DownloadState.Queued, DownloadState.Queued)
        assertEquals(DownloadState.Verifying, DownloadState.Verifying)
        assertEquals(DownloadState.Verified, DownloadState.Verified)
        assertEquals(DownloadState.Canceled, DownloadState.Canceled)
    }

    private fun assertNullState(state: DownloadState.Running) {
        assertNull(state.speedBytesPerSec)
        assertNull(state.targetSize)
    }
}
