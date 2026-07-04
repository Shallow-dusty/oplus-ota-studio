package dev.shallowdusty.oplusotastudio.feature.lookup

import androidx.annotation.StringRes
import dev.shallowdusty.oplusotastudio.core.model.OtaErrorCategory
import dev.shallowdusty.oplusotastudio.core.model.isLookupReady

data class LookupStateText(
    @StringRes val resId: Int,
    val args: List<Any?> = emptyList(),
)

data class LookupStatePresentation(
    val label: LookupStateText,
    val details: List<LookupStateText> = emptyList(),
    val rawDetails: String? = null,
    val showProgress: Boolean = false,
    val canLookup: Boolean = false,
    val canDownload: Boolean = false,
    val canReset: Boolean = false,
    val canContinuePrivacy: Boolean = false,
    val showExperimentalDisclosure: Boolean = false,
)

fun LookupUiState.toPresentation(): LookupStatePresentation =
    when (this) {
        LookupUiState.Detecting -> LookupStatePresentation(
            label = LookupStateText(R.string.lookup_detecting),
            showProgress = true,
        )

        is LookupUiState.Ready -> LookupStatePresentation(
            label = LookupStateText(R.string.lookup_profile_title),
            details = if (profile.isLookupReady) {
                emptyList()
            } else {
                listOf(LookupStateText(R.string.lookup_detection_incomplete))
            },
            canLookup = profile.isLookupReady,
        )

        LookupUiState.Querying -> LookupStatePresentation(
            label = LookupStateText(R.string.lookup_querying),
            showProgress = true,
        )

        is LookupUiState.PackageFound -> LookupStatePresentation(
            label = LookupStateText(R.string.lookup_update_available),
            details = buildList {
                if (liveLookupExperimental) {
                    add(LookupStateText(R.string.lookup_experimental_notice))
                }
                add(LookupStateText(R.string.lookup_verification_scope))
            },
            canDownload = true,
            canReset = true,
            showExperimentalDisclosure = liveLookupExperimental,
        )

        is LookupUiState.PrivacyDisclosureRequired -> LookupStatePresentation(
            label = LookupStateText(R.string.lookup_before_title),
            details = listOf(
                LookupStateText(R.string.lookup_privacy_body),
                LookupStateText(R.string.lookup_privacy_device_id),
                LookupStateText(R.string.lookup_privacy_no_serial),
            ),
            canContinuePrivacy = true,
            canReset = true,
        )

        LookupUiState.NoUpdate -> LookupStatePresentation(
            label = LookupStateText(R.string.lookup_up_to_date),
            details = listOf(LookupStateText(R.string.lookup_no_update)),
            canReset = true,
        )

        is LookupUiState.Error -> LookupStatePresentation(
            label = LookupStateText(R.string.lookup_failed),
            details = listOf(category.toLookupStateText()),
            rawDetails = raw,
            canReset = true,
        )
    }

private fun OtaErrorCategory.toLookupStateText(): LookupStateText =
    when (this) {
        OtaErrorCategory.Network -> LookupStateText(R.string.lookup_error_network)
        OtaErrorCategory.Server -> LookupStateText(R.string.lookup_error_server)
        OtaErrorCategory.Malformed -> LookupStateText(R.string.lookup_error_malformed)
        OtaErrorCategory.Device -> LookupStateText(R.string.lookup_error_device)
        OtaErrorCategory.File -> LookupStateText(R.string.lookup_error_file)
        OtaErrorCategory.ChecksumMismatch -> LookupStateText(R.string.lookup_error_checksum)
        OtaErrorCategory.Unknown -> LookupStateText(R.string.lookup_error_unknown)
    }
