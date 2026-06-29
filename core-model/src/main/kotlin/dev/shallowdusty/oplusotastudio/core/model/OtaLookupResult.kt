package dev.shallowdusty.oplusotastudio.core.model

/**
 * Outcome of an OTA lookup (spec §1.3). `core-ota` parses both protocol styles
 * into this canonical result.
 *
 * - [PackageFound]: a newer package is available.
 * - [NoUpdate]: the server reports the device is current.
 * - [Error]: the lookup failed; [category] drives UI copy (spec §8) and retry
 *   policy. [raw] is the unparsed response/error for the details view and is
 *   never shown directly to the user.
 */
sealed interface OtaLookupResult {
    data class PackageFound(val pkg: OtaPackage) : OtaLookupResult
    data object NoUpdate : OtaLookupResult
    data class Error(val category: OtaErrorCategory, val raw: String?) : OtaLookupResult
}
