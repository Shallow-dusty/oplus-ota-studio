package dev.shallowdusty.oplusotastudio

import dev.shallowdusty.oplusotastudio.core.ota.LegacyOtaLookupService
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Test

class AppGraphTest {

    @Test
    fun `uses real OTA lookup service`() {
        val graph = AppGraph()

        assertInstanceOf(LegacyOtaLookupService::class.java, graph.otaLookupService)
    }
}
