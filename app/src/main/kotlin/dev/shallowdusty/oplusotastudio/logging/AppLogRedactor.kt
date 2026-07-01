package dev.shallowdusty.oplusotastudio.logging

object AppLogRedactor {
    private val ImeiPattern = Regex("""(?i)\b(imei\s*=\s*)\d{14,16}\b""")
    private val MacPattern = Regex("""(?i)\b(mac\s*=\s*)([0-9a-f]{2}:){5}[0-9a-f]{2}\b""")
    private val SerialPattern = Regex("""(?i)\b(serial(?:number)?\s*=\s*)([^\s,;]+)""")
    private val DownloadUrlPattern = Regex("""(?i)\b((?:signedUrl|downloadUrl)\s*=\s*)[^\s,;]+""")

    fun redact(message: String): String =
        message
            .replace(ImeiPattern) { match -> "${match.groupValues[1]}[REDACTED_IMEI]" }
            .replace(MacPattern) { match -> "${match.groupValues[1]}[REDACTED_MAC]" }
            .replace(SerialPattern) { match ->
                "${match.groupValues[1]}${match.groupValues[2].maskKeepingLastFour()}"
            }
            .replace(DownloadUrlPattern) { match -> "${match.groupValues[1]}[REDACTED_URL]" }

    private fun String.maskKeepingLastFour(): String {
        val visible = takeLast(4)
        val hidden = "*".repeat((length - visible.length).coerceAtLeast(1))
        return hidden + visible
    }
}
