package dev.shallowdusty.oplusotastudio.download

import dev.shallowdusty.oplusotastudio.core.model.DownloadPreferences
import dev.shallowdusty.oplusotastudio.core.model.DownloadPreferencesStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class BatteryDownloadAdmissionGateTest {

    @Test
    fun `rejects new downloads when battery is below user threshold`() = runTest {
        val preferencesStore = RecordingDownloadPreferencesStore(
            DownloadPreferences(batteryPauseThresholdPercent = 20),
        )
        val battery = MutableBatterySnapshotProvider(
            BatterySnapshot(levelPercent = 19, isCharging = false),
        )
        val gate = BatteryDownloadAdmissionGate(
            preferencesStore = preferencesStore,
            batterySnapshotProvider = battery,
            scope = backgroundScope,
        )

        assertEquals(
            "Battery is below 20%; new downloads are paused.",
            gate.rejectionReason(),
        )

        battery.snapshot = BatterySnapshot(levelPercent = 20, isCharging = false)
        assertNull(gate.rejectionReason())

        battery.snapshot = BatterySnapshot(levelPercent = 5, isCharging = true)
        assertNull(gate.rejectionReason())
    }

    @Test
    fun `uses latest battery threshold preference`() = runTest {
        val preferencesStore = RecordingDownloadPreferencesStore(
            DownloadPreferences(batteryPauseThresholdPercent = 15),
        )
        val gate = BatteryDownloadAdmissionGate(
            preferencesStore = preferencesStore,
            batterySnapshotProvider = MutableBatterySnapshotProvider(
                BatterySnapshot(levelPercent = 18, isCharging = false),
            ),
            scope = backgroundScope,
        )

        assertNull(gate.rejectionReason())

        preferencesStore.setBatteryPauseThresholdPercent(20)

        assertEquals(
            "Battery is below 20%; new downloads are paused.",
            gate.rejectionReason(),
        )
    }

    private class MutableBatterySnapshotProvider(
        var snapshot: BatterySnapshot?,
    ) : () -> BatterySnapshot? {
        override fun invoke(): BatterySnapshot? = snapshot
    }

    private class RecordingDownloadPreferencesStore(
        initialPreferences: DownloadPreferences,
    ) : DownloadPreferencesStore {
        private val state = MutableStateFlow(initialPreferences)

        override val preferences: Flow<DownloadPreferences> = state

        override suspend fun setWifiOnly(enabled: Boolean) {
            state.value = state.value.copy(wifiOnly = enabled)
        }

        override suspend fun setBatteryPauseThresholdPercent(percent: Int) {
            state.value = state.value.copy(batteryPauseThresholdPercent = percent)
        }
    }
}
