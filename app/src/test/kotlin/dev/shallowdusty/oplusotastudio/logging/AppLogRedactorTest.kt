package dev.shallowdusty.oplusotastudio.logging

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class AppLogRedactorTest {

    @Test
    fun `redacts imei and mac values`() {
        val raw = "lookup failed imei=490154203237518 mac=AA:BB:CC:DD:EE:FF"

        val redacted = AppLogRedactor.redact(raw)

        assertEquals(
            "lookup failed imei=[REDACTED_IMEI] mac=[REDACTED_MAC]",
            redacted,
        )
    }

    @Test
    fun `masks serial-like values to last four characters`() {
        val raw = "serialNumber=ABCD1234WXYZ serial=short"

        val redacted = AppLogRedactor.redact(raw)

        assertEquals("serialNumber=********WXYZ serial=*hort", redacted)
    }

    @Test
    fun `keeps OTA build strings visible for debugging`() {
        val raw = "otaVersion=LE2120_11.H.23_0001_000000000001"

        val redacted = AppLogRedactor.redact(raw)

        assertEquals(raw, redacted)
    }
}
