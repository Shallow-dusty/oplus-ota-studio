package dev.shallowdusty.oplusotastudio.feature.lookup

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.shallowdusty.oplusotastudio.core.model.DeviceDetector
import dev.shallowdusty.oplusotastudio.core.model.DeviceProfile
import dev.shallowdusty.oplusotastudio.core.model.DownloadEngine
import dev.shallowdusty.oplusotastudio.core.model.OtaErrorCategory
import dev.shallowdusty.oplusotastudio.core.model.OtaLookupResult
import dev.shallowdusty.oplusotastudio.core.model.OtaLookupService
import dev.shallowdusty.oplusotastudio.core.model.OtaPackage
import dev.shallowdusty.oplusotastudio.core.model.OtaProfile
import dev.shallowdusty.oplusotastudio.core.model.OtaRegion
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * UI state for the lookup screen (spec §6). Each branch is an explicit render
 * state so the Composable never has to infer intent from raw data.
 */
sealed interface LookupUiState {

    /** First load: detecting the device before the user can query. */
    data object Detecting : LookupUiState

    /** Idle: a profile is ready (detected or manual), no query in flight. */
    data class Ready(val profile: OtaProfile, val device: DeviceProfile?) : LookupUiState

    /** Query in flight. */
    data object Querying : LookupUiState

    /** A package was found. */
    data class PackageFound(val pkg: OtaPackage) : LookupUiState

    /** Server reports the device is current. */
    data object NoUpdate : LookupUiState

    /** Structured error; [category] drives copy, [raw] for the details view. */
    data class Error(val category: OtaErrorCategory, val raw: String?) : LookupUiState
}

/**
 * Drives the lookup screen. Depends only on [OtaLookupService] and
 * [DeviceDetector] contracts (dependency inversion), so it is pure-JVM testable
 * with fakes and unchanged when the real backend lands.
 */
class LookupViewModel(
    private val deviceDetector: DeviceDetector,
    private val lookupService: OtaLookupService,
    private val downloadEngine: DownloadEngine? = null,
) : ViewModel() {

    private val _uiState = MutableStateFlow<LookupUiState>(LookupUiState.Detecting)
    val uiState: StateFlow<LookupUiState> = _uiState.asStateFlow()

    /** The last Ready profile, so reset() can return to it from a terminal state. */
    private var lastReady: LookupUiState.Ready? = null

    init {
        detectDevice()
    }

    private fun detectDevice() {
        viewModelScope.launch {
            val device = deviceDetector.detect()
            val profile = device.toProfile()
            val ready = if (profile != null) {
                LookupUiState.Ready(profile, device)
            } else {
                // Detection could not produce a usable profile; surface a Ready
                // shell with a placeholder so the user can enter fields manually.
                LookupUiState.Ready(
                    OtaProfile(model = "", region = OtaRegion.Global, otaVersion = ""),
                    device,
                )
            }
            lastReady = ready
            _uiState.value = ready
        }
    }

    /** Run a lookup against the current profile. No-op unless [LookupUiState.Ready]. */
    fun lookup() {
        val ready = _uiState.value as? LookupUiState.Ready ?: return
        val profile = ready.profile
        if (profile.otaVersion.isBlank()) return // spec §2.3: block incomplete profiles
        viewModelScope.launch {
            _uiState.value = LookupUiState.Querying
            _uiState.value = when (val result = lookupService.lookup(profile)) {
                is OtaLookupResult.PackageFound -> LookupUiState.PackageFound(result.pkg)
                OtaLookupResult.NoUpdate -> LookupUiState.NoUpdate
                is OtaLookupResult.Error -> LookupUiState.Error(result.category, result.raw)
            }
        }
    }

    /** Manual override of the profile (spec §5 step 2). */
    fun updateProfile(profile: OtaProfile) {
        val device = (uiState.value as? LookupUiState.Ready)?.device
        val ready = LookupUiState.Ready(profile, device)
        lastReady = ready
        _uiState.value = ready
    }

    /** Reset from a terminal result (PackageFound/NoUpdate/Error) back to Ready. */
    fun reset() {
        lastReady?.let { _uiState.value = it }
    }

    fun enqueueDownload(pkg: OtaPackage) {
        val engine = downloadEngine ?: return
        viewModelScope.launch {
            engine.enqueue(pkg)
        }
    }
}

/** Build an [OtaProfile] from detected facts; null if the build string is missing. */
private fun DeviceProfile.toProfile(): OtaProfile? {
    val model = model ?: return null
    val version = otaVersion ?: return null // spec §2.3: otaVersion is required
    return OtaProfile(
        model = model,
        region = region ?: OtaRegion.Global,
        otaVersion = version,
        systemType = null,
        deviceCodename = product,
    )
}
