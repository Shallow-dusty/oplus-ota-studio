package dev.shallowdusty.oplusotastudio

import android.content.ComponentCallbacks2
import dev.shallowdusty.oplusotastudio.core.download.MutableDownloadAdmissionGate
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class MemoryPressureDownloadGateControllerTest {

    @Test
    @Suppress("DEPRECATION")
    fun `low memory pressure rejects new downloads until running pressure moderates`() {
        val gate = MutableDownloadAdmissionGate()
        val controller = MemoryPressureDownloadGateController(
            gate = gate,
            rejectionReason = "System resource pressure is critical; new downloads are paused.",
        )

        controller.onTrimMemory(ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW)

        assertEquals("System resource pressure is critical; new downloads are paused.", gate.rejectionReason())

        controller.onTrimMemory(ComponentCallbacks2.TRIM_MEMORY_RUNNING_MODERATE)

        assertEquals(null, gate.rejectionReason())
    }
}
