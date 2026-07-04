package dev.shallowdusty.oplusotastudio.logging

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class AppLoggerTest {

    @Test
    fun `release logger drops debug lines but writes info and errors`() {
        val sink = RecordingLogSink()
        val logger = AppLogger(
            sink = sink,
            minLevel = AppLogLevel.Info,
            nowMs = { 42L },
        )

        logger.debug("OTA", "debug host status")
        logger.info("OTA", "lookup started")
        logger.error("OTA", "lookup failed")

        assertEquals(
            listOf(
                LogWrite(AppLogLevel.Info, "OTA", "lookup started", 42L),
                LogWrite(AppLogLevel.Error, "OTA", "lookup failed", 42L),
            ),
            sink.writes,
        )
    }

    @Test
    fun `debug logger keeps debug lines`() {
        val sink = RecordingLogSink()
        val logger = AppLogger(
            sink = sink,
            minLevel = AppLogLevel.Debug,
            nowMs = { 7L },
        )

        logger.debug("HTTP", "status=200")

        assertEquals(
            listOf(LogWrite(AppLogLevel.Debug, "HTTP", "status=200", 7L)),
            sink.writes,
        )
    }

    private class RecordingLogSink : AppLogSink {
        val writes = mutableListOf<LogWrite>()

        override fun append(level: AppLogLevel, tag: String, message: String, nowMs: Long) {
            writes += LogWrite(level, tag, message, nowMs)
        }
    }

    private data class LogWrite(
        val level: AppLogLevel,
        val tag: String,
        val message: String,
        val nowMs: Long,
    )
}
