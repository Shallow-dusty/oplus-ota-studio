package dev.shallowdusty.oplusotastudio.core.storage

import dev.shallowdusty.oplusotastudio.core.model.HistoryEntry
import dev.shallowdusty.oplusotastudio.core.model.OtaRegion
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class RoomPackageRepositoryTest {

    @Test
    fun `record maps domain history entry into entity`() = runTest {
        val dao = FakeHistoryDao()
        val repository = RoomPackageRepository(dao)
        val entry = sampleEntry(id = "h1", lookedUpAtMs = 1000L)

        repository.record(entry)

        assertEquals(
            HistoryEntity(
                id = "h1",
                profileModel = "LE2120",
                profileRegion = "China",
                packageName = "LE2120_14.0.0.1901(CN01)",
                packageSize = 6_559_817_109L,
                lookedUpAtMs = 1000L,
                downloadedAtMs = 2000L,
                localFilePath = "/storage/emulated/0/Download/OPlus/package.zip",
            ),
            dao.upserts.single(),
        )
    }

    @Test
    fun `observeHistory maps entities to newest first domain rows`() = runTest {
        val dao = FakeHistoryDao()
        val repository = RoomPackageRepository(dao)
        dao.rows.value = listOf(
            sampleEntity(id = "old", lookedUpAtMs = 1000L),
            sampleEntity(id = "new", lookedUpAtMs = 2000L),
        )

        val rows = repository.observeHistory().first()

        assertEquals(listOf("new", "old"), rows.map { it.id })
        assertEquals(OtaRegion.China, rows.first().profileRegion)
        assertEquals("LE2120_14.0.0.1901(CN01)", rows.first().packageName)
    }

    @Test
    fun `markDownloaded updates newest matching package row`() = runTest {
        val dao = FakeHistoryDao()
        val repository = RoomPackageRepository(dao)
        dao.rows.value = listOf(
            sampleEntity(id = "old", lookedUpAtMs = 1000L),
            sampleEntity(id = "new", lookedUpAtMs = 2000L),
            sampleEntity(id = "other", lookedUpAtMs = 3000L).copy(packageName = "LE2120_other"),
        )

        repository.markDownloaded(
            packageName = "LE2120_14.0.0.1901(CN01)",
            downloadedAtMs = 4000L,
            localFilePath = "content://downloads/package.zip",
        )

        assertEquals(
            listOf(
                sampleEntity(id = "old", lookedUpAtMs = 1000L),
                sampleEntity(id = "new", lookedUpAtMs = 2000L).copy(
                    downloadedAtMs = 4000L,
                    localFilePath = "content://downloads/package.zip",
                ),
                sampleEntity(id = "other", lookedUpAtMs = 3000L).copy(packageName = "LE2120_other"),
            ),
            dao.rows.value,
        )
    }

    private fun sampleEntry(id: String, lookedUpAtMs: Long): HistoryEntry =
        HistoryEntry(
            id = id,
            profileModel = "LE2120",
            profileRegion = OtaRegion.China,
            packageName = "LE2120_14.0.0.1901(CN01)",
            packageSize = 6_559_817_109L,
            lookedUpAtMs = lookedUpAtMs,
            downloadedAtMs = 2000L,
            localFilePath = "/storage/emulated/0/Download/OPlus/package.zip",
        )

    private fun sampleEntity(id: String, lookedUpAtMs: Long): HistoryEntity =
        HistoryEntity(
            id = id,
            profileModel = "LE2120",
            profileRegion = "China",
            packageName = "LE2120_14.0.0.1901(CN01)",
            packageSize = 6_559_817_109L,
            lookedUpAtMs = lookedUpAtMs,
            downloadedAtMs = null,
            localFilePath = null,
        )

    private class FakeHistoryDao : HistoryDao {
        val rows = MutableStateFlow<List<HistoryEntity>>(emptyList())
        val upserts = mutableListOf<HistoryEntity>()

        override suspend fun upsert(entry: HistoryEntity) {
            upserts += entry
        }

        override suspend fun markDownloaded(
            packageName: String,
            downloadedAtMs: Long,
            localFilePath: String,
        ) {
            val target = rows.value
                .filter { it.packageName == packageName }
                .maxByOrNull { it.lookedUpAtMs }
            rows.value = rows.value.map { row ->
                if (row.id == target?.id) {
                    row.copy(
                        downloadedAtMs = downloadedAtMs,
                        localFilePath = localFilePath,
                    )
                } else {
                    row
                }
            }
        }

        override fun observeAll(): kotlinx.coroutines.flow.Flow<List<HistoryEntity>> = rows
    }
}
