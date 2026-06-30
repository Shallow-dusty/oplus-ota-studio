package dev.shallowdusty.oplusotastudio.logging

import java.io.File
import org.junit.jupiter.api.AfterEach
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
}
