package dev.shallowdusty.oplusotastudio.core.model

/**
 * Queries an OTA server for an available package (spec §1). Implementation
 * (core-ota) builds the protocol-specific request from [profile] and parses the
 * response into [OtaLookupResult]. The app injects a fake during v0.0.
 *
 * Implementations must not throw for expected failure modes (network, server,
 * malformed); they return [OtaLookupResult.Error]. Unexpected internal errors
 * may throw and are caught at the ViewModel boundary.
 */
interface OtaLookupService {
    suspend fun lookup(profile: OtaProfile): OtaLookupResult
}
