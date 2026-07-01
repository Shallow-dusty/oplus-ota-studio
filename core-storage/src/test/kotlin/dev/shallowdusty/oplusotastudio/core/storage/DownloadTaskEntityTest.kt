package dev.shallowdusty.oplusotastudio.core.storage

import dev.shallowdusty.oplusotastudio.core.model.DownloadState
import dev.shallowdusty.oplusotastudio.core.model.OtaPackage
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class DownloadTaskEntityTest {

    @Test
    fun `fromPackage stores OTA package and queued state`() {
        val entity = DownloadTaskEntity.fromPackage(
            taskId = "task-1",
            pkg = samplePackage(),
            tempFilePath = "/cache/task-1.zip.part",
            updatedAtMs = 100L,
        )

        assertEquals("task-1", entity.taskId)
        assertEquals("https://otagm.oppo.com/package.zip", entity.downloadUrl)
        assertEquals("otagm.oppo.com", entity.sourceHost)
        assertEquals("14.0.0.1901", entity.versionName)
        assertEquals("full", entity.packageType)
        assertEquals(6_559_817_109L, entity.packageSize)
        assertEquals("/cache/task-1.zip.part", entity.tempFilePath)
        assertEquals("Queued", entity.state)
        assertEquals(DownloadState.Queued, entity.toDownloadState())
    }

    @Test
    fun `withState returns copy with encoded download state`() {
        val entity = DownloadTaskEntity.fromPackage(
            taskId = "task-1",
            pkg = samplePackage(),
            tempFilePath = "/cache/task-1.zip.part",
            updatedAtMs = 100L,
        )

        val running = entity.withState(
            state = DownloadState.Running(
                downloadedBytes = 4096L,
                targetSize = 8192L,
                speedBytesPerSec = 512L,
            ),
            updatedAtMs = 200L,
        )

        assertEquals("Running", running.state)
        assertEquals(4096L, running.downloadedBytes)
        assertEquals(8192L, running.targetSize)
        assertEquals(512L, running.speedBytesPerSec)
        assertEquals(200L, running.updatedAtMs)
        assertEquals(
            DownloadState.Running(4096L, 8192L, 512L),
            running.toDownloadState(),
        )
    }

    @Test
    fun `withState stores checksum mismatch hashes`() {
        val entity = DownloadTaskEntity.fromPackage(
            taskId = "task-1",
            pkg = samplePackage(),
            tempFilePath = "/cache/task-1.zip.part",
            updatedAtMs = 100L,
        )

        val failed = entity.withState(
            state = DownloadState.Failed(
                category = dev.shallowdusty.oplusotastudio.core.model.OtaErrorCategory.ChecksumMismatch,
                retriesRemaining = 0,
                raw = "quarantined",
                expectedHash = "expected-md5",
                actualHash = "actual-md5",
            ),
            updatedAtMs = 200L,
        )

        assertEquals("expected-md5", failed.expectedHash)
        assertEquals("actual-md5", failed.actualHash)
        assertEquals(
            DownloadState.Failed(
                category = dev.shallowdusty.oplusotastudio.core.model.OtaErrorCategory.ChecksumMismatch,
                retriesRemaining = 0,
                raw = "quarantined",
                expectedHash = "expected-md5",
                actualHash = "actual-md5",
            ),
            failed.toDownloadState(),
        )
    }

    private fun samplePackage(): OtaPackage =
        OtaPackage(
            versionName = "14.0.0.1901",
            type = "full",
            sizeBytes = 6_559_817_109L,
            sourceHost = "otagm.oppo.com",
            downloadUrl = "https://otagm.oppo.com/package.zip",
            md5 = "md5",
            sha256 = "sha256",
            releaseNotes = "notes",
        )
}
