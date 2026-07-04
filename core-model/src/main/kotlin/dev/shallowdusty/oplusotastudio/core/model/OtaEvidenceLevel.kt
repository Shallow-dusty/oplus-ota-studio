package dev.shallowdusty.oplusotastudio.core.model

/**
 * Strength of evidence behind an OTA protocol/package claim (spec § Delivery
 * Rules / Evidence Levels).
 */
enum class OtaEvidenceLevel(
    val stableId: String,
    val requiresExperimentalDisclosure: Boolean,
) {
    LiveVerified("live-verified", requiresExperimentalDisclosure = false),
    CapturedReal("captured-real", requiresExperimentalDisclosure = true),
    ReplayedRealProfile("replayed-real-profile", requiresExperimentalDisclosure = true),
    Synthetic("synthetic", requiresExperimentalDisclosure = true),
}
