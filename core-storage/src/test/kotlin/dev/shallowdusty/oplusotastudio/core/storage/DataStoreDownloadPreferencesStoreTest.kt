package dev.shallowdusty.oplusotastudio.core.storage

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import dev.shallowdusty.oplusotastudio.core.model.DownloadPreferences
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class DataStoreDownloadPreferencesStoreTest {

    @Test
    fun `defaults to wifi only with fifteen percent battery threshold`() = runTest {
        val store = DataStoreDownloadPreferencesStore(testDataStore("defaults", backgroundScope))

        assertEquals(DownloadPreferences(), store.preferences.first())
    }

    @Test
    fun `persists wifi only preference update`() = runTest {
        val store = DataStoreDownloadPreferencesStore(testDataStore("wifi-only", backgroundScope))

        store.setWifiOnly(false)

        assertEquals(
            DownloadPreferences(
                wifiOnly = false,
            ),
            store.preferences.first(),
        )
    }

    @Test
    fun `persists battery pause threshold update`() = runTest {
        val store = DataStoreDownloadPreferencesStore(testDataStore("battery-threshold", backgroundScope))

        store.setBatteryPauseThresholdPercent(20)

        assertEquals(
            DownloadPreferences(
                batteryPauseThresholdPercent = 20,
            ),
            store.preferences.first(),
        )
    }

    private fun testDataStore(
        name: String,
        scope: CoroutineScope,
    ): DataStore<Preferences> {
        val file = File("build/tmp/datastore-download-preferences/$name.preferences_pb")
        file.parentFile?.mkdirs()
        file.delete()
        return PreferenceDataStoreFactory.create(
            scope = scope,
            produceFile = { file },
        )
    }
}
