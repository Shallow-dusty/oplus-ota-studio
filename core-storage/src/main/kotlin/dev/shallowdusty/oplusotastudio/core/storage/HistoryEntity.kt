package dev.shallowdusty.oplusotastudio.core.storage

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "history")
data class HistoryEntity(
    @PrimaryKey val id: String,
    val profileModel: String,
    val profileRegion: String,
    val packageName: String,
    val packageSize: Long,
    val lookedUpAtMs: Long,
    val downloadedAtMs: Long?,
    val localFilePath: String?,
)
