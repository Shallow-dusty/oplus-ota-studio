package dev.shallowdusty.oplusotastudio.download

import dev.shallowdusty.oplusotastudio.core.model.OtaPackage

data class DownloadPromotionTarget(
    val directoryName: String,
    val displayName: String,
    val mimeType: String,
) {
    companion object {
        private const val APP_DOWNLOADS_DIRECTORY = "OPlus OTA Studio"
        private const val ZIP_MIME_TYPE = "application/zip"

        fun fromPackage(pkg: OtaPackage): DownloadPromotionTarget {
            val baseName = pkg.versionName
                .takeIf { it.isNotBlank() }
                ?.let { versionName ->
                    listOfNotNull(versionName, pkg.type?.takeIf { it.isNotBlank() })
                        .joinToString("_")
                }
                ?: pkg.downloadUrl.urlFileName()
                ?: "ota-package"

            return DownloadPromotionTarget(
                directoryName = APP_DOWNLOADS_DIRECTORY,
                displayName = baseName.sanitizedZipName(),
                mimeType = ZIP_MIME_TYPE,
            )
        }

        private fun String.urlFileName(): String? = substringBefore("?")
            .substringBefore("#")
            .substringAfterLast("/")
            .takeIf { it.isNotBlank() }

        private fun String.sanitizedZipName(): String {
            val sanitized = replace(Regex("[^A-Za-z0-9._-]+"), "_")
                .replace(Regex("_+"), "_")
                .trim('_', '.', '-')
                .ifBlank { "ota-package" }

            return if (sanitized.endsWith(".zip", ignoreCase = true)) {
                sanitized
            } else {
                "$sanitized.zip"
            }
        }
    }
}
