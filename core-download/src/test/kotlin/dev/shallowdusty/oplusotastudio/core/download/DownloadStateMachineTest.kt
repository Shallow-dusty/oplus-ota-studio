package dev.shallowdusty.oplusotastudio.core.download

import dev.shallowdusty.oplusotastudio.core.model.DownloadState
import dev.shallowdusty.oplusotastudio.core.model.OtaErrorCategory
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test

class DownloadStateMachineTest {

    private val machine = DownloadStateMachine()

    @Test
    fun `starts a queued task as running`() {
        val next = machine.start(
            current = DownloadState.Queued,
            downloadedBytes = 128L,
            targetSize = 512L,
            speedBytesPerSec = 64L,
        )

        assertEquals(
            DownloadState.Running(
                downloadedBytes = 128L,
                targetSize = 512L,
                speedBytesPerSec = 64L,
            ),
            next,
        )
    }

    @Test
    fun `pauses and resumes a running task`() {
        val running = DownloadState.Running(
            downloadedBytes = 256L,
            targetSize = 1_024L,
            speedBytesPerSec = 128L,
        )

        val paused = machine.pause(running, DownloadState.Paused.PauseReason.User)
        val resumed = machine.resume(
            current = paused,
            downloadedBytes = 256L,
            targetSize = 1_024L,
            speedBytesPerSec = null,
        )

        assertEquals(DownloadState.Paused(DownloadState.Paused.PauseReason.User), paused)
        assertEquals(
            DownloadState.Running(
                downloadedBytes = 256L,
                targetSize = 1_024L,
                speedBytesPerSec = null,
            ),
            resumed,
        )
    }

    @Test
    fun `completed running task enters verifying then verified`() {
        val running = DownloadState.Running(
            downloadedBytes = 1_024L,
            targetSize = 1_024L,
            speedBytesPerSec = null,
        )

        val verifying = machine.complete(running)
        val verified = machine.verificationSucceeded(verifying)

        assertEquals(DownloadState.Verifying, verifying)
        assertEquals(DownloadState.Verified, verified)
    }

    @Test
    fun `checksum mismatch becomes terminal failed state`() {
        val failed = machine.verificationFailed(
            current = DownloadState.Verifying,
            raw = "expected abc but was def",
        )
        val retry = machine.retry(
            current = failed,
            attempt = 1,
            maxAttempts = 3,
        )

        assertEquals(
            DownloadState.Failed(
                category = OtaErrorCategory.ChecksumMismatch,
                retriesRemaining = 0,
                raw = "expected abc but was def",
            ),
            failed,
        )
        assertSame(failed, retry)
    }

    @Test
    fun `retriable network failure can move to retrying`() {
        val failed = machine.fail(
            current = DownloadState.Running(
                downloadedBytes = 300L,
                targetSize = 1_000L,
                speedBytesPerSec = null,
            ),
            category = OtaErrorCategory.Network,
            retriesRemaining = 2,
            raw = "timeout",
        )
        val retrying = machine.retry(
            current = failed,
            attempt = 1,
            maxAttempts = 3,
        )

        assertEquals(
            DownloadState.Failed(
                category = OtaErrorCategory.Network,
                retriesRemaining = 2,
                raw = "timeout",
            ),
            failed,
        )
        assertEquals(
            DownloadState.Retrying(
                attempt = 1,
                maxAttempts = 3,
                category = OtaErrorCategory.Network,
            ),
            retrying,
        )
    }

    @Test
    fun `retry exhaustion stays failed`() {
        val failed = DownloadState.Failed(
            category = OtaErrorCategory.Server,
            retriesRemaining = 0,
            raw = "503",
        )

        val retry = machine.retry(
            current = failed,
            attempt = 3,
            maxAttempts = 3,
        )

        assertSame(failed, retry)
    }

    @Test
    fun `cancel from active non terminal states is terminal canceled`() {
        assertEquals(DownloadState.Canceled, machine.cancel(DownloadState.Queued))
        assertEquals(
            DownloadState.Canceled,
            machine.cancel(DownloadState.Running(1L, 10L, null)),
        )
        assertEquals(
            DownloadState.Canceled,
            machine.cancel(DownloadState.Paused(DownloadState.Paused.PauseReason.NetworkLost)),
        )
    }
}
