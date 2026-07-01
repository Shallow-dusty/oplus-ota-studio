package dev.shallowdusty.oplusotastudio.core.model

import kotlinx.coroutines.flow.Flow

/**
 * Persists lookup history and downloaded-package metadata (spec §5 step 7,
 * §3.3 Room state). Implementation (core-storage) backs this with Room +
 * DataStore. The app injects a fake during v0.0.
 *
 * History survives process death and powers the history list UI.
 */
interface PackageRepository {
    /** A new successful lookup or completed download is recorded here. */
    suspend fun record(entry: HistoryEntry)

    /** Mark the latest matching package row as downloaded after file promotion. */
    suspend fun markDownloaded(packageName: String, downloadedAtMs: Long, localFilePath: String)

    /** Persist checksum mismatch diagnostics for the latest matching package row. */
    suspend fun markChecksumMismatch(
        packageName: String,
        expectedHash: String,
        actualHash: String,
    ) = Unit

    fun observeHistory(): Flow<List<HistoryEntry>>
}

/**
 * One history row: a lookup result (and, if downloaded, the local file info).
 * Keeps enough package metadata to re-open details and copy the package link
 * after process death (spec §5 step 7).
 */
data class HistoryEntry(
    val id: String,
    val profileModel: String,
    val profileRegion: OtaRegion,
    val packageName: String,
    val packageSize: Long,
    val sourceHost: String,
    val downloadUrl: String,
    val md5: String?,
    val sha256: String?,
    val releaseNotes: String?,
    val evidenceLevel: OtaEvidenceLevel,
    val lookedUpAtMs: Long,
    val downloadedAtMs: Long?,
    val localFilePath: String?,
    val checksumExpectedHash: String? = null,
    val checksumActualHash: String? = null,
)
