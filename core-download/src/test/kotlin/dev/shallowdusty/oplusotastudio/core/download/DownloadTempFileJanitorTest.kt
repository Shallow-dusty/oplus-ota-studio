package dev.shallowdusty.oplusotastudio.core.download

import java.io.File
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class DownloadTempFileJanitorTest {

    @Test
    fun `deletes orphaned part files and keeps active part files`() {
        val tempRoot = testTempRoot("delete-orphans")
        val activePart = tempRoot.resolve("active.zip.part").also { it.writeText("active") }
        val orphanPart = tempRoot.resolve("orphan.zip.part").also { it.writeText("orphan") }

        val result = DownloadTempFileJanitor(listOf(tempRoot)).deleteOrphanedParts(
            activeTempFilePaths = setOf(activePart.absolutePath),
        )

        assertTrue(activePart.exists())
        assertFalse(orphanPart.exists())
        assertEquals(listOf(orphanPart.absolutePath), result.deletedPaths)
        assertEquals(emptyList<String>(), result.failedPaths)
    }

    @Test
    fun `deletes quarantined bad ota files`() {
        val tempRoot = testTempRoot("delete-quarantined")
        val badFile = tempRoot.resolve("checksum-mismatch.zip.bad").also { it.writeText("bad") }

        val result = DownloadTempFileJanitor(listOf(tempRoot)).deleteOrphanedParts(
            activeTempFilePaths = emptySet(),
        )

        assertFalse(badFile.exists())
        assertEquals(listOf(badFile.absolutePath), result.deletedPaths)
        assertEquals(emptyList<String>(), result.failedPaths)
    }

    @Test
    fun `ignores files that are not download cleanup candidates`() {
        val tempRoot = testTempRoot("ignore-other-files")
        val zip = tempRoot.resolve("package.zip").also { it.writeText("zip") }
        val text = tempRoot.resolve("note.txt").also { it.writeText("note") }

        val result = DownloadTempFileJanitor(listOf(tempRoot)).deleteOrphanedParts(
            activeTempFilePaths = emptySet(),
        )

        assertTrue(zip.exists())
        assertTrue(text.exists())
        assertEquals(emptyList<String>(), result.deletedPaths)
        assertEquals(emptyList<String>(), result.failedPaths)
    }

    @Test
    fun `normalizes active relative paths before comparing`() {
        val tempRoot = testTempRoot("normalize-active")
        val activePart = tempRoot.resolve("active.zip.part").also { it.writeText("active") }

        DownloadTempFileJanitor(listOf(tempRoot)).deleteOrphanedParts(
            activeTempFilePaths = setOf(activePart.path),
        )

        assertTrue(activePart.exists())
    }

    private fun testTempRoot(name: String): File {
        val dir = File("build/tmp/download-temp-file-janitor-test/$name")
        dir.deleteRecursively()
        dir.mkdirs()
        return dir
    }
}
