package dev.shallowdusty.oplusotastudio.logging

import java.io.File

fun createAppLogger(
    filesDir: File,
    debuggable: Boolean,
): AppLogger =
    AppLogger(
        sink = RollingFileLogStore(logDir = filesDir.resolve("logs")),
        minLevel = if (debuggable) AppLogLevel.Debug else AppLogLevel.Info,
    )

fun createAppLogArchiveExporter(
    filesDir: File,
    nowMs: () -> Long = System::currentTimeMillis,
): AppLogArchiveExporter =
    AppLogArchiveExporter(
        logStore = RollingFileLogStore(logDir = filesDir.resolve("logs")),
        nowMs = nowMs,
    )

fun createAppDiagnosticsProvider(
    filesDir: File,
    nowMs: () -> Long = System::currentTimeMillis,
): AppDiagnosticsProvider {
    val logStore = RollingFileLogStore(logDir = filesDir.resolve("logs"))
    return AppDiagnosticsProvider(
        logStore = logStore,
        logArchiveExporter = AppLogArchiveExporter(
            logStore = logStore,
            nowMs = nowMs,
        ),
    )
}
