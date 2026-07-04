package dev.shallowdusty.oplusotastudio.core.model

import kotlinx.coroutines.flow.Flow

/**
 * User download constraints (spec §3.4). Defaults are conservative for a
 * multi-GB OTA transfer on a phone.
 */
data class DownloadPreferences(
    val wifiOnly: Boolean = true,
    val batteryPauseThresholdPercent: Int = 15,
)

interface DownloadPreferencesStore {
    val preferences: Flow<DownloadPreferences>

    suspend fun setWifiOnly(enabled: Boolean)

    suspend fun setBatteryPauseThresholdPercent(percent: Int)
}
