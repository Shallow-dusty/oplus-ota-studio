package dev.shallowdusty.oplusotastudio.core.ota

import dev.shallowdusty.oplusotastudio.core.model.OtaRegion
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class OtaHostResolverTest {

    @Test
    fun `loads built in versioned host map`() {
        val resolver = OtaHostResolver()

        assertEquals(1, resolver.version)
        assertEquals("otagm.oppo.com", resolver.resolve(OtaRegion.Global))
        assertEquals("otadiu.oppo.com", resolver.resolve(OtaRegion.India))
        assertEquals("otai.oppo.com", resolver.resolve(OtaRegion.International))
        assertEquals("otacn.oppo.com", resolver.resolve(OtaRegion.China))
    }

    @Test
    fun `falls back to global host when a map omits a region`() {
        val resolver = OtaHostResolver(
            hostMap = OtaHostMap(
                version = 7,
                hosts = mapOf(OtaRegion.Global to "example.oppo.test"),
            ),
        )

        assertEquals(7, resolver.version)
        assertEquals("example.oppo.test", resolver.resolve(OtaRegion.China))
    }
}
