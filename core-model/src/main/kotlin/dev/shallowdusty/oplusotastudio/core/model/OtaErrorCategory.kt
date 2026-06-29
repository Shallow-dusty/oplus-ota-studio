package dev.shallowdusty.oplusotastudio.core.model

/**
 * User-understandable error categories (spec §8). Each maps to dedicated UI
 * copy and a retry policy. Used by both lookup ([OtaLookupResult.Error]) and
 * download ([DownloadState.Failed]) flows.
 *
 * Retryable categories (network, server) allow automatic retry with backoff;
 * non-retryable categories (device, file, checksum) require user action.
 */
enum class OtaErrorCategory {
    /** Unsupported model, missing build, invalid region, field validation. */
    Device,

    /** DNS, TLS, timeout, blocked host, interrupted connection, captive network. */
    Network,

    /** Empty response, unexpected schema, HTTP status, OTA service refusal. */
    Server,

    /** Insufficient storage, write denied, final move failure. */
    File,

    /** Checksum mismatch — terminal, do not auto-retry (spec §3.5). */
    ChecksumMismatch,

    /** Malformed/unparseable server response. */
    Malformed,

    /** Anything not covered above; surfaced with raw details. */
    Unknown,
}

/** Whether a failed operation may be retried automatically (spec §3.5). */
val OtaErrorCategory.isRetriable: Boolean
    get() = this == OtaErrorCategory.Network || this == OtaErrorCategory.Server
