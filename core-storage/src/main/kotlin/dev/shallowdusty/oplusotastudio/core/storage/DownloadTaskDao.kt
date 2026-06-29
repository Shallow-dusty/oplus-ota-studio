package dev.shallowdusty.oplusotastudio.core.storage

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface DownloadTaskDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(task: DownloadTaskEntity)

    @Query("SELECT * FROM download_tasks ORDER BY updatedAtMs DESC")
    fun observeAll(): Flow<List<DownloadTaskEntity>>

    @Query("SELECT * FROM download_tasks WHERE taskId = :taskId LIMIT 1")
    suspend fun get(taskId: String): DownloadTaskEntity?

    @Query("DELETE FROM download_tasks WHERE taskId = :taskId")
    suspend fun delete(taskId: String)
}
