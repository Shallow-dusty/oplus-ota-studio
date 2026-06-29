package dev.shallowdusty.oplusotastudio.core.model

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test

class OtaLookupResultTest {

    @Test
    fun `NoUpdate is a singleton object`() {
        // NoUpdate carries no data; the two references must be the same instance.
        assertSame(OtaLookupResult.NoUpdate, OtaLookupResult.NoUpdate)
    }

    @Test
    fun `PackageFound equality is structural on the package`() {
        val pkg = samplePackage(versionName = "11.0.2.2")
        assertEquals(
            OtaLookupResult.PackageFound(pkg),
            OtaLookupResult.PackageFound(pkg.copy()),
        )
        assertNotEquals(
            OtaLookupResult.PackageFound(pkg),
            OtaLookupResult.PackageFound(pkg.copy(versionName = "12.0.0.0")),
        )
    }

    @Test
    fun `Error carries category and raw for the details view`() {
        val err = OtaLookupResult.Error(OtaErrorCategory.Server, raw = "HTTP 503")
        assertEquals(OtaErrorCategory.Server, err.category)
        assertEquals("HTTP 503", err.raw)
    }

    @Test
    fun `Error with null raw is valid for failures with no response body`() {
        val err = OtaLookupResult.Error(OtaErrorCategory.Network, raw = null)
        assertEquals(OtaErrorCategory.Network, err.category)
        assertEquals(null, err.raw)
    }

    private fun samplePackage(versionName: String) = OtaPackage(
        versionName = versionName,
        type = "full",
        sizeBytes = 3_500_000_000L,
        sourceHost = "otagm.oppo.com",
        downloadUrl = "https://otagm.oppo.com/pkg.zip",
        md5 = "abc123",
        sha256 = null,
    )
}
