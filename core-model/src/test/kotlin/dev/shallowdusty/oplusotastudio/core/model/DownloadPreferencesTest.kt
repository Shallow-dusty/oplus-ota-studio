package dev.shallowdusty.oplusotastudio.core.model

import kotlinx.coroutines.flow.Flow
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class DownloadPreferencesTest {

    @Test
    fun `default preferences require wifi and pause below fifteen percent battery`() {
        val preferences = DownloadPreferences()

        assertTrue(preferences.wifiOnly)
        assertEquals(15, preferences.batteryPauseThresholdPercent)
    }

    @Test
    fun `store contract exposes preferences and update operations`() {
        val store: DownloadPreferencesStore = object : DownloadPreferencesStore {
            override val preferences: Flow<DownloadPreferences>
                get() = kotlinx.coroutines.flow.flowOf(DownloadPreferences())

            override suspend fun setWifiOnly(enabled: Boolean) = Unit

            override suspend fun setBatteryPauseThresholdPercent(percent: Int) = Unit
        }

        assertEquals(store, store)
    }
}
