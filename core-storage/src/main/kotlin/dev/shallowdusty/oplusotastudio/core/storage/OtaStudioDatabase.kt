package dev.shallowdusty.oplusotastudio.core.storage

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [
        HistoryEntity::class,
        DownloadTaskEntity::class,
    ],
    version = 2,
    exportSchema = true,
)
abstract class OtaStudioDatabase : RoomDatabase() {
    abstract fun historyDao(): HistoryDao
    abstract fun downloadTaskDao(): DownloadTaskDao
}
