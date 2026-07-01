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

val MIGRATION_2_3: Migration = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `history` ADD COLUMN `sourceHost` TEXT NOT NULL DEFAULT ''")
        db.execSQL("ALTER TABLE `history` ADD COLUMN `downloadUrl` TEXT NOT NULL DEFAULT ''")
        db.execSQL("ALTER TABLE `history` ADD COLUMN `md5` TEXT")
        db.execSQL("ALTER TABLE `history` ADD COLUMN `sha256` TEXT")
        db.execSQL("ALTER TABLE `history` ADD COLUMN `releaseNotes` TEXT")
        db.execSQL("ALTER TABLE `history` ADD COLUMN `evidenceLevel` TEXT NOT NULL DEFAULT 'Synthetic'")
    }
}

val MIGRATION_3_4: Migration = object : Migration(3, 4) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `history` ADD COLUMN `checksumExpectedHash` TEXT")
        db.execSQL("ALTER TABLE `history` ADD COLUMN `checksumActualHash` TEXT")
        db.execSQL("ALTER TABLE `download_tasks` ADD COLUMN `expectedHash` TEXT")
        db.execSQL("ALTER TABLE `download_tasks` ADD COLUMN `actualHash` TEXT")
    }
}
