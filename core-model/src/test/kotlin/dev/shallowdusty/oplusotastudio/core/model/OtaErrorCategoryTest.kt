package dev.shallowdusty.oplusotastudio.core.model

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class OtaErrorCategoryTest {

    @Test
    fun `Network and Server are retriable`() {
        // Spec §3.5: only network/server errors auto-retry with backoff.
        assertTrue(OtaErrorCategory.Network.isRetriable)
        assertTrue(OtaErrorCategory.Server.isRetriable)
    }

    @Test
    fun `ChecksumMismatch is never retried automatically`() {
        // Spec §3.5: re-downloading the same URL yields the same bytes, so a
        // checksum mismatch must not auto-retry — the user decides.
        assertFalse(OtaErrorCategory.ChecksumMismatch.isRetriable)
    }

    @Test
    fun `Device, File, Malformed, Unknown are not retriable`() {
        // These require user action (fix profile, free storage, etc.).
        assertFalse(OtaErrorCategory.Device.isRetriable)
        assertFalse(OtaErrorCategory.File.isRetriable)
        assertFalse(OtaErrorCategory.Malformed.isRetriable)
        assertFalse(OtaErrorCategory.Unknown.isRetriable)
    }
}
