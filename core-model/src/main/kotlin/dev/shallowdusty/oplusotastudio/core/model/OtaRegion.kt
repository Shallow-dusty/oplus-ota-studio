package dev.shallowdusty.oplusotastudio.core.model

/**
 * OTA service region. Maps to the host families in spec §1.1; the backend
 * (core-ota) resolves a region to a concrete CDN host.
 *
 * `Global` is the fallback when no region signal is available (spec §2.2).
 */
enum class OtaRegion {
    Global,
    India,
    International,
    China,
}
