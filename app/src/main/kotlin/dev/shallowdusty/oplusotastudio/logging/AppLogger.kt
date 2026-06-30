package dev.shallowdusty.oplusotastudio.logging

interface AppLogSink {
    fun append(level: AppLogLevel, tag: String, message: String, nowMs: Long = System.currentTimeMillis())
}

class AppLogger(
    private val sink: AppLogSink,
    private val minLevel: AppLogLevel,
    private val nowMs: () -> Long = System::currentTimeMillis,
) {
    fun error(tag: String, message: String) = write(AppLogLevel.Error, tag, message)

    fun warn(tag: String, message: String) = write(AppLogLevel.Warn, tag, message)

    fun info(tag: String, message: String) = write(AppLogLevel.Info, tag, message)

    fun debug(tag: String, message: String) = write(AppLogLevel.Debug, tag, message)

    private fun write(level: AppLogLevel, tag: String, message: String) {
        if (level.priority > minLevel.priority) return
        sink.append(level, tag, message, nowMs())
    }

    private val AppLogLevel.priority: Int
        get() = when (this) {
            AppLogLevel.Error -> 0
            AppLogLevel.Warn -> 1
            AppLogLevel.Info -> 2
            AppLogLevel.Debug -> 3
        }
}
