package dev.shallowdusty.oplusotastudio.logging

import java.io.File

class AppDiagnosticsProvider(
    private val logStore: RollingFileLogStore,
    private val logArchiveExporter: AppLogArchiveExporter,
) {
    fun recentLogLines(limit: Int = DefaultRecentLineLimit): List<String> =
        logStore.readRecentLines(limit)

    fun exportLogs(
        targetZip: File,
        recentLineLimit: Int = DefaultRecentLineLimit,
        taskErrorChain: List<String> = emptyList(),
    ): File =
        logArchiveExporter.exportTo(
            targetZip = targetZip,
            recentLineLimit = recentLineLimit,
            taskErrorChain = taskErrorChain,
        )

    private companion object {
        const val DefaultRecentLineLimit = 500
    }
}
