package dev.shallowdusty.oplusotastudio.core.download

import java.io.File

data class DownloadTempFileJanitorResult(
    val deletedPaths: List<String>,
    val failedPaths: List<String>,
)

class DownloadTempFileJanitor(
    private val tempRoots: List<File>,
) {
    fun deleteOrphanedParts(
        activeTempFilePaths: Set<String>,
    ): DownloadTempFileJanitorResult {
        val activeCanonicalPaths = activeTempFilePaths.mapTo(mutableSetOf()) { path ->
            File(path).canonicalPath
        }
        val deletedPaths = mutableListOf<String>()
        val failedPaths = mutableListOf<String>()

        tempRoots
            .distinctBy { it.canonicalPath }
            .forEach { root ->
                root.listFiles()
                    ?.filter { file -> file.isFile && CleanupSuffixes.any { file.name.endsWith(it) } }
                    ?.forEach { partFile ->
                        val canonicalPath = partFile.canonicalPath
                        if (canonicalPath !in activeCanonicalPaths) {
                            if (partFile.delete()) {
                                deletedPaths += partFile.absolutePath
                            } else {
                                failedPaths += partFile.absolutePath
                            }
                        }
                    }
            }

        return DownloadTempFileJanitorResult(
            deletedPaths = deletedPaths,
            failedPaths = failedPaths,
        )
    }

    private companion object {
        val CleanupSuffixes = listOf(".zip.part", ".zip.bad")
    }
}
