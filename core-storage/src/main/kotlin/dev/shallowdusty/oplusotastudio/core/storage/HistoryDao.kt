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

    @Query("SELECT * FROM history ORDER BY lookedUpAtMs DESC")
    fun observeAll(): Flow<List<HistoryEntity>>
}
