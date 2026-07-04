package dev.shallowdusty.oplusotastudio.download

import android.annotation.TargetApi
import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import dev.shallowdusty.oplusotastudio.core.download.DownloadFilePromoter
import dev.shallowdusty.oplusotastudio.core.download.PromotedDownloadFile
import dev.shallowdusty.oplusotastudio.core.model.OtaPackage
import java.io.File
import java.io.IOException

class AndroidMediaStoreDownloadFilePromoter(
    private val context: Context,
) : DownloadFilePromoter {

    override suspend fun promote(
        taskId: String,
        pkg: OtaPackage,
        sourceFile: File,
    ): PromotedDownloadFile {
        val target = DownloadPromotionTarget.fromPackage(pkg)
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            promoteWithMediaStore(target, sourceFile)
        } else {
            promoteWithLegacyDownloads(target, sourceFile)
        }
    }

    @TargetApi(Build.VERSION_CODES.Q)
    private fun promoteWithMediaStore(
        target: DownloadPromotionTarget,
        sourceFile: File,
    ): PromotedDownloadFile {
        val resolver = context.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, target.displayName)
            put(MediaStore.MediaColumns.MIME_TYPE, target.mimeType)
            put(
                MediaStore.MediaColumns.RELATIVE_PATH,
                "${Environment.DIRECTORY_DOWNLOADS}/${target.directoryName}",
            )
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
        val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
            ?: throw IOException("Unable to create download entry")

        try {
            sourceFile.inputStream().use { input ->
                resolver.openOutputStream(uri)?.use { output ->
                    input.copyTo(output)
                } ?: throw IOException("Unable to open download output stream")
            }
            resolver.update(
                uri,
                ContentValues().apply {
                    put(MediaStore.MediaColumns.IS_PENDING, 0)
                },
                null,
                null,
            )
            return PromotedDownloadFile(finalFilePath = uri.toString())
        } catch (error: IOException) {
            resolver.delete(uri, null, null)
            throw error
        }
    }

    @Suppress("DEPRECATION")
    private fun promoteWithLegacyDownloads(
        target: DownloadPromotionTarget,
        sourceFile: File,
    ): PromotedDownloadFile {
        val destinationDir = Environment
            .getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            .resolve(target.directoryName)
        destinationDir.mkdirs()
        val destination = destinationDir.resolve(target.displayName)
        sourceFile.copyTo(destination, overwrite = true)
        return PromotedDownloadFile(finalFilePath = destination.absolutePath)
    }
}
