package dev.shallowdusty.oplusotastudio.core.storage

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import dev.shallowdusty.oplusotastudio.core.model.DownloadPreferences
import dev.shallowdusty.oplusotastudio.core.model.DownloadPreferencesStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class DataStoreDownloadPreferencesStore(
    private val dataStore: DataStore<Preferences>,
) : DownloadPreferencesStore {

    override val preferences: Flow<DownloadPreferences> =
        dataStore.data.map { values ->
            DownloadPreferences(
                wifiOnly = values[WifiOnlyKey] ?: DownloadPreferences().wifiOnly,
                batteryPauseThresholdPercent = values[BatteryPauseThresholdPercentKey]
                    ?: DownloadPreferences().batteryPauseThresholdPercent,
            )
        }

    override suspend fun setWifiOnly(enabled: Boolean) {
        dataStore.edit { values ->
            values[WifiOnlyKey] = enabled
        }
    }

    override suspend fun setBatteryPauseThresholdPercent(percent: Int) {
        dataStore.edit { values ->
            values[BatteryPauseThresholdPercentKey] = percent
        }
    }

    private companion object {
        val WifiOnlyKey = booleanPreferencesKey("download_wifi_only")
        val BatteryPauseThresholdPercentKey =
            intPreferencesKey("download_battery_pause_threshold_percent")
    }
}
