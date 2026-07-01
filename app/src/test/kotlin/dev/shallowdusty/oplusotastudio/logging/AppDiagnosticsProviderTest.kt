package dev.shallowdusty.oplusotastudio.logging

import java.io.File
import java.util.zip.ZipInputStream
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class AppDiagnosticsProviderTest {

    private val root = File("build/tmp/app-diagnostics-provider-test")
    private val logStore = RollingFileLogStore(logDir = root.resolve("logs"), maxBytes = 1024)

    @AfterEach
    fun tearDown() {
        root.deleteRecursively()
    }

    @Test
    fun `reads recent app log lines`() {
        logStore.append(AppLogLevel.Info, "Download", "queued", nowMs = 1L)
        logStore.append(AppLogLevel.Warn, "Download", "retrying", nowMs = 2L)
        val provider = AppDiagnosticsProvider(
            logStore = logStore,
            logArchiveExporter = AppLogArchiveExporter(logStore = logStore, nowMs = { 10L }),
        )

        assertEquals(
            listOf("2\tWARN\tDownload\tretrying"),
            provider.recentLogLines(limit = 1),
        )
    }

    @Test
    fun `exports recent app logs`() {
        logStore.append(AppLogLevel.Info, "Download", "old", nowMs = 1L)
        logStore.append(AppLogLevel.Error, "OTA", "new", nowMs = 2L)
        val provider = AppDiagnosticsProvider(
            logStore = logStore,
            logArchiveExporter = AppLogArchiveExporter(logStore = logStore, nowMs = { 20L }),
        )

        val zipFile = provider.exportLogs(
            targetZip = root.resolve("diagnostics/logs.zip"),
            recentLineLimit = 1,
        )

        val entries = zipEntries(zipFile)
        assertEquals("createdAtMs=20\nrecentLineLimit=1\n", entries.getValue("manifest.txt"))
        assertEquals("2\tERROR\tOTA\tnew\n", entries.getValue("logs/app.log"))
    }

    @Test
    fun `exports current task error chain with redaction`() {
        val provider = AppDiagnosticsProvider(
            logStore = logStore,
            logArchiveExporter = AppLogArchiveExporter(logStore = logStore, nowMs = { 25L }),
        )

        val zipFile = provider.exportLogs(
            targetZip = root.resolve("diagnostics/errors.zip"),
            taskErrorChain = listOf(
                "Download failed",
                "HTTP 503 signedUrl=https://example.invalid/token.zip imei=490154203237518",
            ),
        )

        val taskErrors = zipEntries(zipFile).getValue("diagnostics/current-task-errors.txt")
        assertTrue(taskErrors.contains("Download failed"))
        assertTrue(taskErrors.contains("imei=[REDACTED_IMEI]"))
        assertTrue(taskErrors.contains("signedUrl=[REDACTED_URL]"))
        assertTrue(!taskErrors.contains("490154203237518"))
    }

    @Test
    fun `factory uses files dir rolling log store`() {
        val logger = createAppLogger(filesDir = root, debuggable = false)
        logger.info("Download", "queued")

        val provider = createAppDiagnosticsProvider(
            filesDir = root,
            nowMs = { 30L },
        )

        assertTrue(provider.recentLogLines(limit = 10).single().contains("INFO\tDownload\tqueued"))
        assertEquals(
            "createdAtMs=30\nrecentLineLimit=500\n",
            zipEntries(provider.exportLogs(root.resolve("logs.zip"))).getValue("manifest.txt"),
        )
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
