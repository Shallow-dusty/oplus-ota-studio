package dev.shallowdusty.oplusotastudio.core.download

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class DownloadStoragePreflightTest {

    @Test
    fun `same volume requires double package size plus one gib`() {
        val preflight = DownloadStoragePreflight()
        val packageSize = 4L * GIB

        val result = preflight.check(
            packageSizeBytes = packageSize,
            snapshot = DownloadStorageSnapshot(
                tempAvailableBytes = 9L * GIB,
                finalAvailableBytes = 9L * GIB,
                tempAndFinalShareVolume = true,
            ),
        )

        assertEquals(DownloadStoragePreflightResult.Passed, result)
    }

    @Test
    fun `same volume fails when free bytes are below double package size plus one gib`() {
        val preflight = DownloadStoragePreflight()
        val packageSize = 4L * GIB

        val result = preflight.check(
            packageSizeBytes = packageSize,
            snapshot = DownloadStorageSnapshot(
                tempAvailableBytes = 9L * GIB - 1,
                finalAvailableBytes = 9L * GIB - 1,
                tempAndFinalShareVolume = true,
            ),
        )

        val failed = result as DownloadStoragePreflightResult.Failed
        assertEquals(9L * GIB, failed.requiredTempBytes)
        assertEquals(9L * GIB, failed.requiredFinalBytes)
        assertTrue(failed.reason.contains("requires 9663676416 bytes"))
    }

    @Test
    fun `different volumes require package size plus one gib on each volume`() {
        val preflight = DownloadStoragePreflight()
        val packageSize = 4L * GIB

        val result = preflight.check(
            packageSizeBytes = packageSize,
            snapshot = DownloadStorageSnapshot(
                tempAvailableBytes = 5L * GIB,
                finalAvailableBytes = 5L * GIB,
                tempAndFinalShareVolume = false,
            ),
        )

        assertEquals(DownloadStoragePreflightResult.Passed, result)
    }

    @Test
    fun `different volumes report temp and final shortages separately`() {
        val preflight = DownloadStoragePreflight()
        val packageSize = 4L * GIB

        val result = preflight.check(
            packageSizeBytes = packageSize,
            snapshot = DownloadStorageSnapshot(
                tempAvailableBytes = 5L * GIB - 1,
                finalAvailableBytes = 5L * GIB - 2,
                tempAndFinalShareVolume = false,
            ),
        )

        val failed = result as DownloadStoragePreflightResult.Failed
        assertEquals(5L * GIB, failed.requiredTempBytes)
        assertEquals(5L * GIB, failed.requiredFinalBytes)
        assertEquals(5L * GIB - 1, failed.availableTempBytes)
        assertEquals(5L * GIB - 2, failed.availableFinalBytes)
    }

    @Test
    fun `unknown package size passes preflight`() {
        val preflight = DownloadStoragePreflight()

        val result = preflight.check(
            packageSizeBytes = 0L,
            snapshot = DownloadStorageSnapshot(
                tempAvailableBytes = 0L,
                finalAvailableBytes = 0L,
                tempAndFinalShareVolume = true,
            ),
        )

        assertEquals(DownloadStoragePreflightResult.Passed, result)
    }

    private companion object {
        const val GIB = 1024L * 1024L * 1024L
    }
}
