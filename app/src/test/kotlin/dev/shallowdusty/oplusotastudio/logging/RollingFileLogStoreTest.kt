package dev.shallowdusty.oplusotastudio.logging

import java.io.File
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class RollingFileLogStoreTest {

    private val logDir = File("build/tmp/rolling-file-log-store-test")

    @AfterEach
    fun tearDown() {
        logDir.deleteRecursively()
    }

    @Test
    fun `append redacts PII before persisting log line`() {
        val store = RollingFileLogStore(logDir = logDir, maxBytes = 1024)

        store.append(
            level = AppLogLevel.Info,
            tag = "Lookup",
            message = "imei=490154203237518 serialNumber=ABCD1234WXYZ",
            nowMs = 1000L,
        )

        val text = logDir.resolve("app.log").readText()
        assertTrue(text.contains("imei=[REDACTED_IMEI]"))
        assertTrue(text.contains("serialNumber=********WXYZ"))
        assertFalse(text.contains("490154203237518"))
        assertFalse(text.contains("ABCD1234WXYZ"))
    }

    @Test
    fun `append trims oldest lines when log exceeds cap`() {
        val store = RollingFileLogStore(logDir = logDir, maxBytes = 120)

        store.append(AppLogLevel.Info, "Download", "old-aaaaaaaaaaaaaaaaaaaa", nowMs = 1L)
        store.append(AppLogLevel.Warn, "Download", "middle-bbbbbbbbbbbbbbbbbbbb", nowMs = 2L)
        store.append(AppLogLevel.Error, "Download", "new-cccccccccccccccccccc", nowMs = 3L)

        val file = logDir.resolve("app.log")
        val text = file.readText()
        assertTrue(file.length() <= 120L)
        assertFalse(text.contains("old-aaaaaaaaaaaaaaaaaaaa"))
        assertTrue(text.contains("middle-bbbbbbbbbbbbbbbbbbbb"))
        assertTrue(text.contains("new-cccccccccccccccccccc"))
        assertEquals(
            listOf(
                "2\tWARN\tDownload\tmiddle-bbbbbbbbbbbbbbbbbbbb",
                "3\tERROR\tDownload\tnew-cccccccccccccccccccc",
            ),
            store.readRecentLines(limit = 10),
        )
    }
}
