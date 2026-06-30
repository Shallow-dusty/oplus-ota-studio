package dev.shallowdusty.oplusotastudio.core.storage

import dev.shallowdusty.oplusotastudio.core.model.DownloadState
import dev.shallowdusty.oplusotastudio.core.model.OtaErrorCategory
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class DownloadStateStorageCodecTest {

    @Test
    fun `running state preserves progress fields`() {
        val columns = DownloadStateStorageCodec.toColumns(
            DownloadState.Running(
                downloadedBytes = 128L,
                targetSize = 1024L,
                speedBytesPerSec = 64L,
            ),
        )

        assertEquals("Running", columns.state)
        assertEquals(128L, columns.downloadedBytes)
        assertEquals(1024L, columns.targetSize)
        assertEquals(64L, columns.speedBytesPerSec)
        assertEquals(
            DownloadState.Running(128L, 1024L, 64L),
            DownloadStateStorageCodec.toDomain(columns),
        )
    }

    @Test
    fun `paused state preserves pause reason`() {
        val columns = DownloadStateStorageCodec.toColumns(
            DownloadState.Paused(DownloadState.Paused.PauseReason.MeteredNetwork),
        )

        assertEquals("Paused", columns.state)
        assertEquals("MeteredNetwork", columns.pauseReason)
        assertEquals(
            DownloadState.Paused(DownloadState.Paused.PauseReason.MeteredNetwork),
            DownloadStateStorageCodec.toDomain(columns),
        )
    }

    @Test
    fun `failed state preserves retry metadata`() {
        val columns = DownloadStateStorageCodec.toColumns(
            DownloadState.Failed(
                category = OtaErrorCategory.Server,
                retriesRemaining = 2,
                raw = "HTTP 503",
            ),
        )

        assertEquals("Failed", columns.state)
        assertEquals("Server", columns.errorCategory)
        assertEquals(2, columns.retriesRemaining)
        assertEquals("HTTP 503", columns.rawError)
        assertEquals(
            DownloadState.Failed(
                category = OtaErrorCategory.Server,
                retriesRemaining = 2,
                raw = "HTTP 503",
            ),
            DownloadStateStorageCodec.toDomain(columns),
        )
    }

    @Test
    fun `unverified state round trips as terminal state`() {
        val columns = DownloadStateStorageCodec.toColumns(DownloadState.Unverified)

        assertEquals("Unverified", columns.state)
        assertEquals(DownloadState.Unverified, DownloadStateStorageCodec.toDomain(columns))
    }
}
