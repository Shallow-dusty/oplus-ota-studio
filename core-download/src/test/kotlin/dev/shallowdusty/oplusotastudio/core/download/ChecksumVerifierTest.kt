package dev.shallowdusty.oplusotastudio.core.download

import java.io.File
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class ChecksumVerifierTest {

    @Test
    fun `prefers sha256 when both hashes are available`() {
        val file = testFile("package-sha256.zip")

        val result = ChecksumVerifier().verify(
            file = file,
            expectedSha256 = "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad",
            expectedMd5 = "wrong-md5-is-ignored",
        )

        assertEquals(
            ChecksumResult.Verified(
                algorithm = ChecksumAlgorithm.Sha256,
                actualHash = "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad",
            ),
            result,
        )
    }

    @Test
    fun `falls back to md5 when sha256 is absent`() {
        val file = testFile("package-md5.zip")

        val result = ChecksumVerifier().verify(
            file = file,
            expectedSha256 = null,
            expectedMd5 = "900150983cd24fb0d6963f7d28e17f72",
        )

        assertEquals(
            ChecksumResult.Verified(
                algorithm = ChecksumAlgorithm.Md5,
                actualHash = "900150983cd24fb0d6963f7d28e17f72",
            ),
            result,
        )
    }

    @Test
    fun `marks package unverified when no hash is available`() {
        val file = testFile("package-unverified.zip")

        val result = ChecksumVerifier().verify(
            file = file,
            expectedSha256 = null,
            expectedMd5 = null,
        )

        assertEquals(ChecksumResult.Unverified, result)
    }

    @Test
    fun `returns mismatch with expected and actual hashes`() {
        val file = testFile("package-mismatch.zip")

        val result = ChecksumVerifier().verify(
            file = file,
            expectedSha256 = "0000000000000000000000000000000000000000000000000000000000000000",
            expectedMd5 = null,
        )

        assertEquals(
            ChecksumResult.Mismatch(
                algorithm = ChecksumAlgorithm.Sha256,
                expectedHash = "0000000000000000000000000000000000000000000000000000000000000000",
                actualHash = "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad",
            ),
            result,
        )
    }

    private fun testFile(name: String): File {
        val dir = File("build/tmp/checksum-verifier-test").also { it.mkdirs() }
        return dir.resolve(name).also { it.writeText("abc") }
    }
}
