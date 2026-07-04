package dev.shallowdusty.oplusotastudio.core.storage

import dev.shallowdusty.oplusotastudio.core.model.HistoryEntry
import dev.shallowdusty.oplusotastudio.core.model.OtaEvidenceLevel
import dev.shallowdusty.oplusotastudio.core.model.OtaRegion
import dev.shallowdusty.oplusotastudio.core.model.PackageRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class RoomPackageRepository(
    private val historyDao: HistoryDao,
) : PackageRepository {

    override suspend fun record(entry: HistoryEntry) {
        historyDao.upsert(entry.toEntity())
    }

    override suspend fun markDownloaded(
        packageName: String,
        sourceHost: String,
        downloadUrl: String,
        downloadedAtMs: Long,
        localFilePath: String,
    ) {
        historyDao.markDownloaded(
            packageName = packageName,
            sourceHost = sourceHost,
            downloadUrl = downloadUrl,
            downloadedAtMs = downloadedAtMs,
            localFilePath = localFilePath,
        )
    }

    override suspend fun markChecksumMismatch(
        packageName: String,
        sourceHost: String,
        downloadUrl: String,
        expectedHash: String,
        actualHash: String,
    ) {
        historyDao.markChecksumMismatch(
            packageName = packageName,
            sourceHost = sourceHost,
            downloadUrl = downloadUrl,
            expectedHash = expectedHash,
            actualHash = actualHash,
        )
    }

    override fun observeHistory(): Flow<List<HistoryEntry>> =
        historyDao.observeAll().map { rows ->
            rows.sortedByDescending { it.lookedUpAtMs }.map { it.toDomain() }
        }
}

private fun HistoryEntry.toEntity(): HistoryEntity =
    HistoryEntity(
        id = id,
        profileModel = profileModel,
        profileRegion = profileRegion.name,
        packageName = packageName,
        packageSize = packageSize,
        sourceHost = sourceHost,
        downloadUrl = downloadUrl,
        md5 = md5,
        sha256 = sha256,
        releaseNotes = releaseNotes,
        evidenceLevel = evidenceLevel.name,
        lookedUpAtMs = lookedUpAtMs,
        downloadedAtMs = downloadedAtMs,
        localFilePath = localFilePath,
        checksumExpectedHash = checksumExpectedHash,
        checksumActualHash = checksumActualHash,
    )

private fun HistoryEntity.toDomain(): HistoryEntry =
    HistoryEntry(
        id = id,
        profileModel = profileModel,
        profileRegion = runCatching { OtaRegion.valueOf(profileRegion) }.getOrDefault(OtaRegion.Global),
        packageName = packageName,
        packageSize = packageSize,
        sourceHost = sourceHost,
        downloadUrl = downloadUrl,
        md5 = md5,
        sha256 = sha256,
        releaseNotes = releaseNotes,
        evidenceLevel = runCatching { OtaEvidenceLevel.valueOf(evidenceLevel) }
            .getOrDefault(OtaEvidenceLevel.Synthetic),
        lookedUpAtMs = lookedUpAtMs,
        downloadedAtMs = downloadedAtMs,
        localFilePath = localFilePath,
        checksumExpectedHash = checksumExpectedHash,
        checksumActualHash = checksumActualHash,
    )
