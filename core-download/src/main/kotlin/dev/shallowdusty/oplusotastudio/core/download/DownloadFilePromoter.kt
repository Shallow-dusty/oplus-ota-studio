package dev.shallowdusty.oplusotastudio.core.download

import dev.shallowdusty.oplusotastudio.core.model.OtaPackage
import java.io.File

data class PromotedDownloadFile(
    val finalFilePath: String,
)

interface DownloadFilePromoter {
    suspend fun promote(
        taskId: String,
        pkg: OtaPackage,
        sourceFile: File,
    ): PromotedDownloadFile
}
