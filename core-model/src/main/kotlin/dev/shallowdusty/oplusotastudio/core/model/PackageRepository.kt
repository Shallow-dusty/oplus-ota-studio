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
    fun observeHistory(): Flow<List<HistoryEntry>>
}

/**
 * One history row: a lookup result (and, if downloaded, the local file info).
 * Kept deliberately small — the details view pulls more from logs (spec §9).
 */
data class HistoryEntry(
    val id: String,
    val profileModel: String,
    val profileRegion: OtaRegion,
    val packageName: String,
    val packageSize: Long,
    val lookedUpAtMs: Long,
    val downloadedAtMs: Long?,
    val localFilePath: String?,
)
