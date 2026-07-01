package dev.shallowdusty.oplusotastudio.core.model

/**
 * A discoverable OTA package (spec §1.3). The canonical domain model that both
 * protocol styles (OnePlus XML, OPlus JSON) parse into.
 *
 * Hashes are nullable because the server may provide neither (spec §4.1 — the
 * package is then marked `unverified` and the UI warns explicitly).
 *
 * [evidenceLevel] defaults to [OtaEvidenceLevel.Synthetic] so generated
 * fixtures and fakes never imply live server verification by accident.
 */
data class OtaPackage(
    val versionName: String,
    /** "full" / "incremental" / null when the server omits it. */
    val type: String?,
    val sizeBytes: Long,
    val sourceHost: String,
    val downloadUrl: String,
    val md5: String? = null,
    val sha256: String? = null,
    val releaseNotes: String? = null,
    val evidenceLevel: OtaEvidenceLevel = OtaEvidenceLevel.Synthetic,
)
