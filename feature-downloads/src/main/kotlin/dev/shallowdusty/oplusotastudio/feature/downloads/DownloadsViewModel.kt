package dev.shallowdusty.oplusotastudio.feature.downloads

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.shallowdusty.oplusotastudio.core.model.DownloadEngine
import dev.shallowdusty.oplusotastudio.core.model.DownloadState
import dev.shallowdusty.oplusotastudio.core.model.DownloadTask
import dev.shallowdusty.oplusotastudio.core.model.HistoryEntry
import dev.shallowdusty.oplusotastudio.core.model.OtaPackage
import dev.shallowdusty.oplusotastudio.core.model.PackageRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * One row in the downloads list UI. Pairs a task with the latest state observed
 * from its [DownloadTask.state] flow.
 */
data class DownloadRow(
    val taskId: String,
    val state: DownloadState,
)

data class HistoryRow(
    val id: String,
    val packageName: String,
    val packageSize: Long,
    val sourceHost: String,
    val downloadUrl: String,
    val evidenceLabel: String,
    val localFilePath: String?,
)

/**
 * UI state for the downloads screen (spec §6). The list reflects the engine's
 * task queue; each row renders from its [DownloadState] branch.
 */
data class DownloadsUiState(
    val rows: List<DownloadRow> = emptyList(),
    val historyRows: List<HistoryRow> = emptyList(),
)

/**
 * Drives the downloads screen. Depends only on the [DownloadEngine] contract
 * (dependency inversion): it observes the queue and forwards pause/resume/cancel
 * to each task. Pure-JVM testable with a fake engine; unchanged when the real
 * core-download lands.
 */
class DownloadsViewModel(
    private val engine: DownloadEngine,
    private val packageRepository: PackageRepository? = null,
) : ViewModel() {

    private val _uiState = MutableStateFlow(DownloadsUiState())
    val uiState: StateFlow<DownloadsUiState> = _uiState.asStateFlow()

    private val latest = mutableMapOf<String, DownloadState>()
    private val taskHandles = mutableMapOf<String, DownloadTask>()
    private val stateJobs = mutableMapOf<String, Job>()
    private val historyEntries = mutableMapOf<String, HistoryEntry>()

    init {
        observeQueue()
        observeHistory()
    }

    private fun observeQueue() {
        viewModelScope.launch {
            engine.observeAll().collect { tasks ->
                val observedTaskIds = tasks.mapTo(mutableSetOf()) { it.taskId }
                val removedTaskIds = latest.keys - observedTaskIds
                removedTaskIds.forEach { taskId ->
                    latest.remove(taskId)
                    taskHandles.remove(taskId)
                    stateJobs.remove(taskId)?.cancel()
                }

                // Subscribe to any new task's state flow.
                tasks.forEach { task ->
                    taskHandles[task.taskId] = task
                    if (task.taskId !in stateJobs) {
                        latest[task.taskId] = DownloadState.Queued
                        stateJobs[task.taskId] = viewModelScope.launch {
                            task.state.collect { state ->
                                latest[task.taskId] = state
                                emitRows()
                            }
                        }
                    }
                }
                emitRows()
            }
        }
    }

    private fun emitRows() {
        _uiState.value = DownloadsUiState(
            rows = latest.entries.map { DownloadRow(it.key, it.value) },
            historyRows = _uiState.value.historyRows,
        )
    }

    private fun observeHistory() {
        val repository = packageRepository ?: return
        viewModelScope.launch {
            repository.observeHistory().collect { entries ->
                historyEntries.clear()
                entries.associateByTo(historyEntries) { it.id }
                _uiState.value = _uiState.value.copy(
                    historyRows = entries.map { it.toHistoryRow() },
                )
            }
        }
    }

    fun pause(taskId: String) {
        val task = taskHandles[taskId] ?: return
        viewModelScope.launch { task.pause() }
    }

    fun resume(taskId: String) {
        val task = taskHandles[taskId] ?: return
        viewModelScope.launch { task.resume() }
    }

    fun cancel(taskId: String) {
        val task = taskHandles[taskId] ?: return
        viewModelScope.launch { task.cancel() }
    }

    fun enqueueHistoryPackage(historyId: String) {
        val entry = historyEntries[historyId] ?: return
        viewModelScope.launch {
            engine.enqueue(entry.toOtaPackage())
        }
    }
}

private fun HistoryEntry.toHistoryRow(): HistoryRow =
    HistoryRow(
        id = id,
        packageName = packageName,
        packageSize = packageSize,
        sourceHost = sourceHost,
        downloadUrl = downloadUrl,
        evidenceLabel = evidenceLevel.stableId,
        localFilePath = localFilePath,
    )

private fun HistoryEntry.toOtaPackage(): OtaPackage =
    OtaPackage(
        versionName = packageName,
        type = null,
        sizeBytes = packageSize,
        sourceHost = sourceHost,
        downloadUrl = downloadUrl,
        md5 = md5,
        sha256 = sha256,
        releaseNotes = releaseNotes,
        evidenceLevel = evidenceLevel,
    )
