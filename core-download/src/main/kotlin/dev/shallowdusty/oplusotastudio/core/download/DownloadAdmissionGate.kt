package dev.shallowdusty.oplusotastudio.core.download

import dev.shallowdusty.oplusotastudio.core.model.DownloadState

fun interface DownloadAdmissionGate {
    fun rejectionReason(): String?

    fun rejectionPauseReason(): DownloadState.Paused.PauseReason? = null

    companion object {
        val AllowAll = DownloadAdmissionGate { null }
    }
}

class MutableDownloadAdmissionGate(
    initialRejectionReason: String? = null,
) : DownloadAdmissionGate {
    @Volatile
    private var currentRejectionReason: String? = initialRejectionReason

    override fun rejectionReason(): String? = currentRejectionReason

    fun rejectNewDownloads(reason: String) {
        currentRejectionReason = reason
    }

    fun allowNewDownloads() {
        currentRejectionReason = null
    }
}
