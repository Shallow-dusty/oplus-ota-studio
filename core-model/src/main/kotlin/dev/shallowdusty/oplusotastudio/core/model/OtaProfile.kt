package dev.shallowdusty.oplusotastudio.core.model

/**
 * The profile used to query an OTA server (spec §1.2). This is the resolved,
 * user-confirmed profile — it may originate from auto-detection
 * ([DeviceProfile]) or from manual entry. The backend (core-ota) builds the
 * concrete request body from this.
 *
 * [otaVersion] is required: a lookup cannot be sent without a build string
 * (spec §2.3 blocks lookup when the profile is incomplete). [region] drives
 * host selection (spec §1.1).
 */
data class OtaProfile(
    val model: String,
    val region: OtaRegion,
    /** Full build string, e.g. `11.0.2.2.LE28AA`. Required. */
    val otaVersion: String,
    /** Optional OEM system type hint, e.g. "Oxygen OS" / "Color OS". */
    val systemType: String? = null,
    /** Optional device codename, distinct from marketing model. */
    val deviceCodename: String? = null,
)
