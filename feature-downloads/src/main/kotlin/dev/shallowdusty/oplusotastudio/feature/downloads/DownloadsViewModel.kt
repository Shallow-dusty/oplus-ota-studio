package dev.shallowdusty.oplusotastudio.feature.downloads

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.shallowdusty.oplusotastudio.core.model.DownloadEngine
import dev.shallowdusty.oplusotastudio.core.model.DownloadState
import dev.shallowdusty.oplusotastudio.core.model.DownloadTask
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

/**
 * UI state for the downloads screen (spec §6). The list reflects the engine's
 * task queue; each row renders from its [DownloadState] branch.
 */
data class DownloadsUiState(
    val rows: List<DownloadRow> = emptyList(),
)

/**
 * Drives the downloads screen. Depends only on the [DownloadEngine] contract
 * (dependency inversion): it observes the queue and forwards pause/resume/cancel
 * to each task. Pure-JVM testable with a fake engine; unchanged when the real
 * core-download lands.
 */
class DownloadsViewModel(
    private val engine: DownloadEngine,
) : ViewModel() {

    private val _uiState = MutableStateFlow(DownloadsUiState())
    val uiState: StateFlow<DownloadsUiState> = _uiState.asStateFlow()

    private val latest = mutableMapOf<String, DownloadState>()

    init {
        observeQueue()
    }

    private fun observeQueue() {
        viewModelScope.launch {
            engine.observeAll().collect { tasks ->
                // Subscribe to any new task's state flow.
                tasks.forEach { task ->
                    if (task.taskId !in latest) {
                        latest[task.taskId] = DownloadState.Queued
                        viewModelScope.launch {
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
        )
    }

    fun pause(taskId: String, task: DownloadTask) {
        viewModelScope.launch { task.pause() }
    }

    fun resume(taskId: String, task: DownloadTask) {
        viewModelScope.launch { task.resume() }
    }

    fun cancel(taskId: String, task: DownloadTask) {
        viewModelScope.launch { task.cancel() }
    }
}
