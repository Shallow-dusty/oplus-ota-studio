package dev.shallowdusty.oplusotastudio.core.storage

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [HistoryEntity::class],
    version = 1,
    exportSchema = true,
)
abstract class OtaStudioDatabase : RoomDatabase() {
    abstract fun historyDao(): HistoryDao
}
