package dev.shallowdusty.oplusotastudio.core.download

data class DownloadStorageSnapshot(
    val tempAvailableBytes: Long,
    val finalAvailableBytes: Long,
    val tempAndFinalShareVolume: Boolean,
)

sealed interface DownloadStoragePreflightResult {
    data object Passed : DownloadStoragePreflightResult

    data class Failed(
        val requiredTempBytes: Long,
        val availableTempBytes: Long,
        val requiredFinalBytes: Long,
        val availableFinalBytes: Long,
        val reason: String,
    ) : DownloadStoragePreflightResult
}

class DownloadStoragePreflight(
    private val reserveBytes: Long = GIB,
) {
    fun check(
        packageSizeBytes: Long,
        snapshot: DownloadStorageSnapshot,
        existingTempBytes: Long = 0L,
    ): DownloadStoragePreflightResult {
        if (packageSizeBytes <= 0L) return DownloadStoragePreflightResult.Passed
        val retainedTempBytes = existingTempBytes.coerceIn(0L, packageSizeBytes)
        val remainingTempBytes = packageSizeBytes - retainedTempBytes

        val requiredTempBytes: Long
        val requiredFinalBytes: Long
        if (snapshot.tempAndFinalShareVolume) {
            val requiredBytes = remainingTempBytes + packageSizeBytes + reserveBytes
            requiredTempBytes = requiredBytes
            requiredFinalBytes = requiredBytes
        } else {
            requiredTempBytes = remainingTempBytes + reserveBytes
            requiredFinalBytes = packageSizeBytes + reserveBytes
        }

        val hasTempBytes = snapshot.tempAvailableBytes >= requiredTempBytes
        val hasFinalBytes = snapshot.finalAvailableBytes >= requiredFinalBytes
        return if (hasTempBytes && hasFinalBytes) {
            DownloadStoragePreflightResult.Passed
        } else {
            DownloadStoragePreflightResult.Failed(
                requiredTempBytes = requiredTempBytes,
                availableTempBytes = snapshot.tempAvailableBytes,
                requiredFinalBytes = requiredFinalBytes,
                availableFinalBytes = snapshot.finalAvailableBytes,
                reason = buildReason(requiredTempBytes, requiredFinalBytes, snapshot),
            )
        }
    }

    private fun buildReason(
        requiredTempBytes: Long,
        requiredFinalBytes: Long,
        snapshot: DownloadStorageSnapshot,
    ): String = if (snapshot.tempAndFinalShareVolume) {
        "Download requires $requiredTempBytes bytes on the shared temp/final volume"
    } else {
        "Download requires $requiredTempBytes temp bytes and $requiredFinalBytes final bytes"
    }

    private companion object {
        const val GIB = 1024L * 1024L * 1024L
    }
}
