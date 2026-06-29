package dev.shallowdusty.oplusotastudio.core.download

data class ResumeSnapshot(
    val acceptRanges: Boolean,
    val downloadedBytes: Long,
    val partFileBytes: Long,
    val etag: String?,
    val lastModified: String?,
)

data class ResumeValidators(
    val etag: String?,
    val lastModified: String?,
)

data class ResumeRequestPlan(
    val rangeStart: Long,
    val discardPartial: Boolean,
    val truncateToBytes: Long?,
    val reason: String?,
)

class ResumeRequestPlanner {
    fun plan(
        stored: ResumeSnapshot,
        current: ResumeValidators,
    ): ResumeRequestPlan {
        if (!stored.acceptRanges) {
            return restart("Server does not support range resume.")
        }

        if (validatorsChanged(stored, current)) {
            return restart("Remote package changed, restarting from 0.")
        }

        return ResumeRequestPlan(
            rangeStart = stored.downloadedBytes.coerceAtLeast(0L),
            discardPartial = false,
            truncateToBytes = stored.downloadedBytes.takeIf { stored.partFileBytes > it },
            reason = null,
        )
    }

    private fun validatorsChanged(
        stored: ResumeSnapshot,
        current: ResumeValidators,
    ): Boolean {
        val etagChanged = stored.etag != null &&
            current.etag != null &&
            stored.etag != current.etag
        val lastModifiedChanged = stored.lastModified != null &&
            current.lastModified != null &&
            stored.lastModified != current.lastModified
        return etagChanged || lastModifiedChanged
    }

    private fun restart(reason: String): ResumeRequestPlan =
        ResumeRequestPlan(
            rangeStart = 0L,
            discardPartial = true,
            truncateToBytes = null,
            reason = reason,
        )
}
