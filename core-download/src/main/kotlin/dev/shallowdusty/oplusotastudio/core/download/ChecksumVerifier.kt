package dev.shallowdusty.oplusotastudio.core.download

import java.io.File
import java.security.MessageDigest

enum class ChecksumAlgorithm(val digestName: String) {
    Sha256("SHA-256"),
    Md5("MD5"),
}

sealed interface ChecksumResult {
    data class Verified(
        val algorithm: ChecksumAlgorithm,
        val actualHash: String,
    ) : ChecksumResult

    data object Unverified : ChecksumResult

    data class Mismatch(
        val algorithm: ChecksumAlgorithm,
        val expectedHash: String,
        val actualHash: String,
    ) : ChecksumResult
}

class ChecksumVerifier(
    private val bufferSizeBytes: Int = 1024 * 1024,
) {
    fun verify(
        file: File,
        expectedSha256: String?,
        expectedMd5: String?,
    ): ChecksumResult {
        val expectation = when {
            !expectedSha256.isNullOrBlank() -> ExpectedChecksum(
                algorithm = ChecksumAlgorithm.Sha256,
                hash = expectedSha256.normalizedHash(),
            )
            !expectedMd5.isNullOrBlank() -> ExpectedChecksum(
                algorithm = ChecksumAlgorithm.Md5,
                hash = expectedMd5.normalizedHash(),
            )
            else -> return ChecksumResult.Unverified
        }

        val actualHash = file.digestHex(expectation.algorithm)
        return if (actualHash == expectation.hash) {
            ChecksumResult.Verified(expectation.algorithm, actualHash)
        } else {
            ChecksumResult.Mismatch(
                algorithm = expectation.algorithm,
                expectedHash = expectation.hash,
                actualHash = actualHash,
            )
        }
    }

    private fun File.digestHex(algorithm: ChecksumAlgorithm): String {
        val digest = MessageDigest.getInstance(algorithm.digestName)
        inputStream().use { input ->
            val buffer = ByteArray(bufferSizeBytes)
            while (true) {
                val read = input.read(buffer)
                if (read == -1) break
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { byte -> "%02x".format(byte) }
    }

    private data class ExpectedChecksum(
        val algorithm: ChecksumAlgorithm,
        val hash: String,
    )

    private fun String.normalizedHash(): String = trim().lowercase()
}
