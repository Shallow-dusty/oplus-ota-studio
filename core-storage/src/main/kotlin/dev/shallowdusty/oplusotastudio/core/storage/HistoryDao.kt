package dev.shallowdusty.oplusotastudio.core.storage

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface HistoryDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entry: HistoryEntity)

    @Query(
        """
        UPDATE history
        SET downloadedAtMs = :downloadedAtMs, localFilePath = :localFilePath
        WHERE id = (
            SELECT id FROM history
            WHERE packageName = :packageName
                AND sourceHost = :sourceHost
                AND downloadUrl = :downloadUrl
            ORDER BY lookedUpAtMs DESC
            LIMIT 1
        )
        """,
    )
    suspend fun markDownloaded(
        packageName: String,
        sourceHost: String,
        downloadUrl: String,
        downloadedAtMs: Long,
        localFilePath: String,
    )

    @Query(
        """
        UPDATE history
        SET checksumExpectedHash = :expectedHash, checksumActualHash = :actualHash
        WHERE id = (
            SELECT id FROM history
            WHERE packageName = :packageName
                AND sourceHost = :sourceHost
                AND downloadUrl = :downloadUrl
            ORDER BY lookedUpAtMs DESC
            LIMIT 1
        )
        """,
    )
    suspend fun markChecksumMismatch(
        packageName: String,
        sourceHost: String,
        downloadUrl: String,
        expectedHash: String,
        actualHash: String,
    )

    @Query("SELECT * FROM history ORDER BY lookedUpAtMs DESC")
    fun observeAll(): Flow<List<HistoryEntity>>
}
