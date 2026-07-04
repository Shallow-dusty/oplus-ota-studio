package dev.shallowdusty.oplusotastudio.feature.downloads

import dev.shallowdusty.oplusotastudio.core.model.DownloadState
import dev.shallowdusty.oplusotastudio.core.model.OtaErrorCategory
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class DownloadStatePresentationTest {

    @Test
    fun `queued and canceled have terminal labels without task actions`() {
        val queued = DownloadState.Queued.toPresentation()
        val canceled = DownloadState.Canceled.toPresentation()

        assertEquals(R.string.downloads_state_queued, queued.label.resId)
        assertFalse(queued.canPause)
        assertFalse(queued.canResume)
        assertFalse(queued.canCancel)

        assertEquals(R.string.downloads_state_canceled, canceled.label.resId)
        assertFalse(canceled.canPause)
        assertFalse(canceled.canResume)
        assertFalse(canceled.canCancel)
    }

    @Test
    fun `running shows bounded progress and pause cancel actions`() {
        val presentation = DownloadState.Running(
            downloadedBytes = 600L,
            targetSize = 1_000L,
            speedBytesPerSec = 20L,
        ).toPresentation()

        assertEquals(R.string.downloads_progress, presentation.label.resId)
        assertEquals(listOf(600L, 1_000L), presentation.label.args)
        assertEquals(0.6f, presentation.progressFraction)
        assertEquals(20L, presentation.speedBytesPerSec)
        assertTrue(presentation.canPause)
        assertTrue(presentation.canCancel)
        assertFalse(presentation.canResume)
    }

    @Test
    fun `running without target uses indeterminate progress`() {
        val presentation = DownloadState.Running(
            downloadedBytes = 600L,
            targetSize = null,
            speedBytesPerSec = null,
        ).toPresentation()

        assertEquals(R.string.downloads_progress, presentation.label.resId)
        assertEquals(listOf(600L, null), presentation.label.args)
        assertNull(presentation.progressFraction)
        assertNull(presentation.speedBytesPerSec)
    }

    @Test
    fun `paused shows reason and resume cancel actions`() {
        val presentation = DownloadState.Paused(
            DownloadState.Paused.PauseReason.MeteredNetwork,
        ).toPresentation()

        assertEquals(R.string.downloads_state_paused, presentation.label.resId)
        assertEquals(listOf("meterednetwork"), presentation.label.args)
        assertTrue(presentation.canResume)
        assertTrue(presentation.canCancel)
        assertFalse(presentation.canPause)
    }

    @Test
    fun `retrying shows retry attempt and indeterminate progress`() {
        val presentation = DownloadState.Retrying(
            attempt = 2,
            maxAttempts = 3,
            category = OtaErrorCategory.Network,
        ).toPresentation()

        assertEquals(R.string.downloads_state_retrying, presentation.label.resId)
        assertEquals(listOf(2, 3, "network"), presentation.label.args)
        assertTrue(presentation.showIndeterminateProgress)
        assertFalse(presentation.canCancel)
    }

    @Test
    fun `verifying shows indeterminate checksum progress`() {
        val presentation = DownloadState.Verifying.toPresentation()

        assertEquals(R.string.downloads_verifying, presentation.label.resId)
        assertTrue(presentation.showIndeterminateProgress)
    }

    @Test
    fun `verified and unverified carry integrity copy`() {
        val verified = DownloadState.Verified.toPresentation()
        val unverified = DownloadState.Unverified.toPresentation()

        assertEquals(R.string.downloads_transfer_verified, verified.label.resId)
        assertEquals(R.string.downloads_transfer_verified_body, verified.details.single().resId)

        assertEquals(R.string.downloads_unverified, unverified.label.resId)
        assertEquals(R.string.downloads_unverified_body, unverified.details.single().resId)
    }

    @Test
    fun `failed checksum mismatch exposes category raw retries and hashes`() {
        val presentation = DownloadState.Failed(
            category = OtaErrorCategory.ChecksumMismatch,
            retriesRemaining = 0,
            raw = "checksum mismatch",
            expectedHash = "abc",
            actualHash = "def",
        ).toPresentation()

        assertEquals(R.string.downloads_state_failed, presentation.label.resId)
        assertEquals(listOf("checksummismatch"), presentation.label.args)
        assertEquals("checksum mismatch", presentation.rawDetails)
        assertEquals(
            listOf(
                DownloadStateText(R.string.downloads_expected_hash, listOf("abc")),
                DownloadStateText(R.string.downloads_actual_hash, listOf("def")),
            ),
            presentation.details,
        )
    }

    @Test
    fun `failed retriable state exposes retries remaining`() {
        val presentation = DownloadState.Failed(
            category = OtaErrorCategory.Network,
            retriesRemaining = 2,
            raw = null,
        ).toPresentation()

        assertEquals(
            DownloadStateText(R.string.downloads_retries_remaining, listOf(2)),
            presentation.details.single(),
        )
    }
}
