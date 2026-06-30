package dev.shallowdusty.oplusotastudio.logging

import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class AppLogArchiveExporter(
    private val logStore: RollingFileLogStore,
    private val nowMs: () -> Long = System::currentTimeMillis,
    private val redactor: (String) -> String = AppLogRedactor::redact,
) {
    fun exportTo(
        targetZip: File,
        recentLineLimit: Int = DefaultRecentLineLimit,
    ): File {
        targetZip.parentFile?.mkdirs()
        ZipOutputStream(targetZip.outputStream()).use { zip ->
            zip.writeEntry(
                name = "manifest.txt",
                text = "createdAtMs=${nowMs()}\nrecentLineLimit=$recentLineLimit\n",
            )
            zip.writeEntry(
                name = "logs/app.log",
                text = logStore.readRecentLines(recentLineLimit)
                    .joinToString(separator = "\n", postfix = "\n") { line -> redactor(line) },
            )
        }
        return targetZip
    }

    private fun ZipOutputStream.writeEntry(name: String, text: String) {
        putNextEntry(ZipEntry(name))
        write(text.toByteArray(Charsets.UTF_8))
        closeEntry()
    }

    private companion object {
        const val DefaultRecentLineLimit = 500
    }
}
