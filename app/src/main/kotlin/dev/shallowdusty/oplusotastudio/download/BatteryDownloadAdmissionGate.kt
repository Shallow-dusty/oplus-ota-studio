package dev.shallowdusty.oplusotastudio.download

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import dev.shallowdusty.oplusotastudio.core.download.DownloadAdmissionGate
import dev.shallowdusty.oplusotastudio.core.model.DownloadPreferencesStore
import dev.shallowdusty.oplusotastudio.core.model.DownloadState
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

data class BatterySnapshot(
    val levelPercent: Int,
    val isCharging: Boolean,
)

class AndroidBatterySnapshotProvider(
    private val context: Context,
) : () -> BatterySnapshot? {
    override fun invoke(): BatterySnapshot? {
        val intent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
            ?: return null
        val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
        if (level < 0 || scale <= 0) return null
        val status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, BatteryManager.BATTERY_STATUS_UNKNOWN)
        return BatterySnapshot(
            levelPercent = (level * 100) / scale,
            isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
                status == BatteryManager.BATTERY_STATUS_FULL,
        )
    }
}

class BatteryDownloadAdmissionGate(
    private val preferencesStore: DownloadPreferencesStore,
    private val batterySnapshotProvider: () -> BatterySnapshot?,
    scope: CoroutineScope,
) : DownloadAdmissionGate {
    @Volatile
    private var latestThresholdPercent: Int = DefaultBatteryPauseThresholdPercent

    init {
        scope.launch(start = CoroutineStart.UNDISPATCHED) {
            preferencesStore.preferences.collect { preferences ->
                latestThresholdPercent = preferences.batteryPauseThresholdPercent
            }
        }
    }

    override fun rejectionReason(): String? {
        val snapshot = batterySnapshotProvider() ?: return null
        if (snapshot.isCharging) return null
        val threshold = currentThresholdPercent()
        if (threshold <= 0 || snapshot.levelPercent >= threshold) return null
        return "Battery is below $threshold%; new downloads are paused."
    }

    override fun rejectionPauseReason(): DownloadState.Paused.PauseReason? =
        if (rejectionReason() != null) DownloadState.Paused.PauseReason.BatteryLow else null

    private fun currentThresholdPercent(): Int {
        val preferences = preferencesStore.preferences
        return if (preferences is StateFlow) {
            preferences.value.batteryPauseThresholdPercent
        } else {
            latestThresholdPercent
        }
    }

    private companion object {
        const val DefaultBatteryPauseThresholdPercent = 15
    }
}
