package dev.shallowdusty.oplusotastudio.core.download

fun interface DownloadAdmissionGate {
    fun rejectionReason(): String?

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
