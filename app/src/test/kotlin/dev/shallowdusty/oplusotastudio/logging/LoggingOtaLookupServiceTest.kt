package dev.shallowdusty.oplusotastudio.logging

import dev.shallowdusty.oplusotastudio.core.model.OtaErrorCategory
import dev.shallowdusty.oplusotastudio.core.model.OtaLookupResult
import dev.shallowdusty.oplusotastudio.core.model.OtaLookupService
import dev.shallowdusty.oplusotastudio.core.model.OtaPackage
import dev.shallowdusty.oplusotastudio.core.model.OtaProfile
import dev.shallowdusty.oplusotastudio.core.model.OtaRegion
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class LoggingOtaLookupServiceTest {

    @Test
    fun `logs lookup start and package found without response body`() = runTest {
        val sink = RecordingLogSink()
        val delegate = FixedLookupService(OtaLookupResult.PackageFound(samplePackage()))
        val service = LoggingOtaLookupService(
            delegate = delegate,
            logger = AppLogger(sink = sink, minLevel = AppLogLevel.Debug, nowMs = { 10L }),
        )

        val result = service.lookup(sampleProfile())

        assertSame(delegate.result, result)
        assertEquals(
            listOf(
                LogWrite(AppLogLevel.Info, "OTA", "lookup started model=LE2120 region=China otaVersion=LE2120_11.H.23_0001_000000000001"),
                LogWrite(AppLogLevel.Info, "OTA", "package found version=LE2120_14.0.0.1901(CN01) host=otacn.oppo.com sizeBytes=6559817109"),
            ),
            sink.writes,
        )
        assertFalse(sink.messages().any { it.contains("<root>") || it.contains("signedUrl") })
    }

    @Test
    fun `logs no update result`() = runTest {
        val sink = RecordingLogSink()
        val service = LoggingOtaLookupService(
            delegate = FixedLookupService(OtaLookupResult.NoUpdate),
            logger = AppLogger(sink = sink, minLevel = AppLogLevel.Info, nowMs = { 11L }),
        )

        service.lookup(sampleProfile())

        assertTrue(sink.writes.any { it.level == AppLogLevel.Info && it.message == "no update" })
    }

    @Test
    fun `logs error category without raw response`() = runTest {
        val sink = RecordingLogSink()
        val service = LoggingOtaLookupService(
            delegate = FixedLookupService(
                OtaLookupResult.Error(
                    category = OtaErrorCategory.Server,
                    raw = "HTTP 503: <root>signedUrl=https://example.invalid/token.zip</root>",
                ),
            ),
            logger = AppLogger(sink = sink, minLevel = AppLogLevel.Debug, nowMs = { 12L }),
        )

        service.lookup(sampleProfile())

        assertTrue(sink.writes.any { it.level == AppLogLevel.Error && it.message == "lookup failed category=Server" })
        assertFalse(sink.messages().any { it.contains("HTTP 503") || it.contains("signedUrl") || it.contains("<root>") })
    }

    private class FixedLookupService(
        val result: OtaLookupResult,
    ) : OtaLookupService {
        override suspend fun lookup(profile: OtaProfile): OtaLookupResult = result
    }

    private class RecordingLogSink : AppLogSink {
        val writes = mutableListOf<LogWrite>()

        override fun append(level: AppLogLevel, tag: String, message: String, nowMs: Long) {
            writes += LogWrite(level, tag, message)
        }

        fun messages(): List<String> = writes.map { it.message }
    }

    private data class LogWrite(
        val level: AppLogLevel,
        val tag: String,
        val message: String,
    )

    private fun sampleProfile(): OtaProfile =
        OtaProfile(
            model = "LE2120",
            region = OtaRegion.China,
            otaVersion = "LE2120_11.H.23_0001_000000000001",
        )

    private fun samplePackage(): OtaPackage =
        OtaPackage(
            versionName = "LE2120_14.0.0.1901(CN01)",
            type = "full",
            sizeBytes = 6_559_817_109L,
            sourceHost = "otacn.oppo.com",
            downloadUrl = "https://example.invalid/pkg.zip",
            md5 = "5ae1e4d8101218d58c1da10092b22996",
        )
}
