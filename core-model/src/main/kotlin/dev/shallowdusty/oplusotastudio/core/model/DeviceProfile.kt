package dev.shallowdusty.oplusotastudio.core.model

/**
 * Raw device facts gathered by [DeviceDetector] (spec §2.1).
 *
 * Fields are nullable because non-root detection is best-effort: hidden-API
 * policy blocks most `ro.*` reads on Android 9+, so marketing name, OS version,
 * and region are frequently `null` (spec §2.3). The UI must treat manual entry
 * as the primary path, not assume these are populated.
 *
 * [buildDisplay] comes from `Build.DISPLAY` and is the most reliable source for
 * recovering the OTA build string via parsing (spec §2.3).
 */
data class DeviceProfile(
    val model: String?,
    val product: String?,
    val marketingName: String?,
    val otaVersion: String?,
    val buildDisplay: String?,
    val androidVersion: String?,
    val securityPatch: String?,
    val region: OtaRegion?,
    /** Last 4 chars only; never the full serial. null if unreadable. */
    val serialSuffix: String?,
    /** True if any signal failed to be read; UI should prompt for manual entry. */
    val incomplete: Boolean,
)
