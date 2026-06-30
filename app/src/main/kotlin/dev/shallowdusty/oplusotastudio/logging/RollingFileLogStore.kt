package dev.shallowdusty.oplusotastudio.logging

import java.io.File

enum class AppLogLevel {
    Error,
    Warn,
    Info,
    Debug,
}

class RollingFileLogStore(
    private val logDir: File,
    private val maxBytes: Long = 5L * 1024L * 1024L,
    private val redactor: (String) -> String = AppLogRedactor::redact,
) {
    private val logFile: File
        get() = logDir.resolve("app.log")

    fun append(
        level: AppLogLevel,
        tag: String,
        message: String,
        nowMs: Long = System.currentTimeMillis(),
    ) {
        logDir.mkdirs()
        logFile.appendText("${nowMs}\t${level.wireName}\t${tag}\t${redactor(message)}\n")
        trimToCap()
    }

    fun readRecentLines(limit: Int): List<String> =
        if (!logFile.exists() || limit <= 0) {
            emptyList()
        } else {
            logFile.readLines().takeLast(limit)
        }

    private fun trimToCap() {
        if (maxBytes <= 0L) {
            logFile.writeText("")
            return
        }
        if (logFile.length() <= maxBytes) return

        val lines = logFile.readLines()
        val retained = ArrayDeque<String>()
        var bytes = 0L
        lines.asReversed().forEach { line ->
            val lineBytes = line.toByteArray().size + 1L
            if (retained.isEmpty() || bytes + lineBytes <= maxBytes) {
                retained.addFirst(line)
                bytes += lineBytes
            }
        }
        logFile.writeText(retained.joinToString(separator = "\n", postfix = "\n"))
    }

    private val AppLogLevel.wireName: String
        get() = when (this) {
            AppLogLevel.Error -> "ERROR"
            AppLogLevel.Warn -> "WARN"
            AppLogLevel.Info -> "INFO"
            AppLogLevel.Debug -> "DEBUG"
        }
}
