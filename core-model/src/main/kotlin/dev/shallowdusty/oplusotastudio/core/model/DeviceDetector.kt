package dev.shallowdusty.oplusotastudio.core.model

/**
 * Detects device facts without root (spec §2). Implementation lives in a
 * backend module; the app injects a fake during v0.0.
 *
 * Must degrade silently to [DeviceProfile.incomplete] = true on any read
 * failure — never throw (spec §2.3).
 */
interface DeviceDetector {
    suspend fun detect(): DeviceProfile
}
