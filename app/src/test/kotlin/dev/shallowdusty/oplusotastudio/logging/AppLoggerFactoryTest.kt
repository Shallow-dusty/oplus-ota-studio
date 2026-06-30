package dev.shallowdusty.oplusotastudio.logging

import java.io.File
import java.util.zip.ZipInputStream
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class AppLoggerFactoryTest {

    private val filesDir = File("build/tmp/app-logger-factory-test")

    @AfterEach
    fun tearDown() {
        filesDir.deleteRecursively()
    }

    @Test
    fun `release logger writes info to files dir logs and drops debug`() {
        val logger = createAppLogger(
            filesDir = filesDir,
            debuggable = false,
        )

        logger.debug("HTTP", "status=200")
        logger.info("Download", "queued")

        val text = filesDir.resolve("logs/app.log").readText()
        assertFalse(text.contains("DEBUG"))
        assertFalse(text.contains("status=200"))
        assertTrue(text.contains("INFO\tDownload\tqueued"))
    }

    @Test
    fun `debug logger keeps debug records`() {
        val logger = createAppLogger(
            filesDir = filesDir,
            debuggable = true,
        )

        logger.debug("HTTP", "status=200")

        val text = filesDir.resolve("logs/app.log").readText()
        assertTrue(text.contains("DEBUG\tHTTP\tstatus=200"))
    }

    @Test
    fun `creates log archive exporter for files dir logs`() {
        val logger = createAppLogger(
            filesDir = filesDir,
            debuggable = false,
        )
        logger.info("Download", "queued")

        val exporter = createAppLogArchiveExporter(
            filesDir = filesDir,
            nowMs = { 99L },
        )

        val entries = zipEntries(exporter.exportTo(filesDir.resolve("logs.zip")))
        assertEquals("createdAtMs=99\nrecentLineLimit=500\n", entries.getValue("manifest.txt"))
        assertTrue(entries.getValue("logs/app.log").contains("INFO\tDownload\tqueued"))
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
