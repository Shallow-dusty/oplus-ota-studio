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
