package dev.shallowdusty.oplusotastudio.core.storage

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

val MIGRATION_1_2: Migration = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `download_tasks` (
                `taskId` TEXT NOT NULL,
                `downloadUrl` TEXT NOT NULL,
                `sourceHost` TEXT NOT NULL,
                `versionName` TEXT NOT NULL,
                `packageType` TEXT NOT NULL,
                `packageSize` INTEGER NOT NULL,
                `md5` TEXT,
                `sha256` TEXT,
                `releaseNotes` TEXT,
                `tempFilePath` TEXT NOT NULL,
                `finalFilePath` TEXT,
                `etag` TEXT,
                `lastModified` TEXT,
                `acceptRanges` INTEGER NOT NULL,
                `downloadedBytes` INTEGER NOT NULL,
                `targetSize` INTEGER,
                `speedBytesPerSec` INTEGER,
                `state` TEXT NOT NULL,
                `pauseReason` TEXT,
                `retryAttempt` INTEGER,
                `maxRetryAttempts` INTEGER,
                `errorCategory` TEXT,
                `retriesRemaining` INTEGER,
                `rawError` TEXT,
                `updatedAtMs` INTEGER NOT NULL,
                PRIMARY KEY(`taskId`)
            )
            """.trimIndent(),
        )
    }
}
