package dev.shallowdusty.oplusotastudio.download

import android.content.Context
import android.os.Environment
import android.os.storage.StorageManager
import dev.shallowdusty.oplusotastudio.core.download.DownloadStorageSnapshot
import java.io.File

class AndroidDownloadStorageSnapshotProvider(
    context: Context,
    private val tempRoot: File,
    private val finalRoot: File = Environment.getExternalStoragePublicDirectory(
        Environment.DIRECTORY_DOWNLOADS,
    ),
) : () -> DownloadStorageSnapshot {
    private val storageManager = context.getSystemService(StorageManager::class.java)

    override fun invoke(): DownloadStorageSnapshot {
        tempRoot.mkdirs()
        finalRoot.mkdirs()
        return DownloadStorageSnapshot(
            tempAvailableBytes = tempRoot.usableSpace,
            finalAvailableBytes = finalRoot.usableSpace,
            tempAndFinalShareVolume = tempAndFinalShareVolume(),
        )
    }

    private fun tempAndFinalShareVolume(): Boolean =
        runCatching {
            storageManager.getUuidForPath(tempRoot) == storageManager.getUuidForPath(finalRoot)
        }.getOrDefault(
            tempRoot.absolutePath.substringBefore("/Android/") ==
                finalRoot.absolutePath.substringBefore("/Download"),
        )
}
