package dev.shallowdusty.oplusotastudio.logging

import java.io.File
import java.util.zip.ZipInputStream
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class AppLogArchiveExporterTest {

    private val root = File("build/tmp/app-log-archive-exporter-test")
    private val logDir = root.resolve("logs")

    @AfterEach
    fun tearDown() {
        root.deleteRecursively()
    }

    @Test
    fun `exports recent log lines and manifest into zip`() {
        logDir.mkdirs()
        logDir.resolve("app.log").writeText(
            listOf(
                "1\tINFO\tDownload\told",
                "2\tWARN\tDownload\tmiddle",
                "3\tERROR\tOTA\tnew",
            ).joinToString(separator = "\n", postfix = "\n"),
        )
        logDir.resolve("unrelated.tmp").writeText("do not export")
        val exporter = AppLogArchiveExporter(
            logStore = RollingFileLogStore(logDir = logDir, maxBytes = 1024),
            nowMs = { 1234L },
        )

        val zipFile = exporter.exportTo(
            targetZip = root.resolve("exports/logs.zip"),
            recentLineLimit = 2,
        )

        val entries = zipEntries(zipFile)
        assertEquals(
            setOf("manifest.txt", "logs/app.log"),
            entries.keys,
        )
        assertEquals(
            "createdAtMs=1234\nrecentLineLimit=2\n",
            entries.getValue("manifest.txt"),
        )
        assertEquals(
            "2\tWARN\tDownload\tmiddle\n3\tERROR\tOTA\tnew\n",
            entries.getValue("logs/app.log"),
        )
        assertFalse(entries.values.any { it.contains("old") || it.contains("do not export") })
    }

    @Test
    fun `redacts exported lines defensively`() {
        logDir.mkdirs()
        logDir.resolve("app.log").writeText("1\tINFO\tOTA\timei=490154203237518 serialNumber=ABCD1234WXYZ\n")
        val exporter = AppLogArchiveExporter(
            logStore = RollingFileLogStore(logDir = logDir, maxBytes = 1024),
            nowMs = { 5678L },
        )

        val entries = zipEntries(exporter.exportTo(root.resolve("logs.zip")))

        val appLog = entries.getValue("logs/app.log")
        assertTrue(appLog.contains("imei=[REDACTED_IMEI]"))
        assertTrue(appLog.contains("serialNumber=********WXYZ"))
        assertFalse(appLog.contains("490154203237518"))
        assertFalse(appLog.contains("ABCD1234WXYZ"))
    }

    private fun zipEntries(zipFile: File): Map<String, String> {
        val entries = linkedMapOf<String, String>()
        ZipInputStream(zipFile.inputStream()).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                entries[entry.name] = zip.readBytes().toString(Charsets.UTF_8)
                zip.closeEntry()
            }
        }
        return entries
    }
}
