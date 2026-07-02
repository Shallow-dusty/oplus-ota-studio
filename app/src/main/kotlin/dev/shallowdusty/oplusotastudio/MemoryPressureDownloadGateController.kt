package dev.shallowdusty.oplusotastudio

import android.content.ComponentCallbacks2
import dev.shallowdusty.oplusotastudio.core.download.MutableDownloadAdmissionGate

class MemoryPressureDownloadGateController(
    private val gate: MutableDownloadAdmissionGate,
    private val rejectionReason: String,
) {
    @Suppress("DEPRECATION")
    fun onTrimMemory(level: Int) {
        when {
            level in ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW..ComponentCallbacks2.TRIM_MEMORY_RUNNING_CRITICAL ->
                gate.rejectNewDownloads(rejectionReason)
            level <= ComponentCallbacks2.TRIM_MEMORY_RUNNING_MODERATE ->
                gate.allowNewDownloads()
        }
    }
}
