package dev.shallowdusty.oplusotastudio.core.model

import kotlinx.coroutines.flow.Flow

/**
 * A handle to one download task. Obtained from [DownloadEngine.enqueue].
 *
 * [state] emits every state transition (spec §3.5); the UI renders from it.
 * The progress fields inside [DownloadState.Running] are throttled per spec §7.
 */
interface DownloadTask {
    val taskId: String
    val state: Flow<DownloadState>
    suspend fun pause()
    suspend fun resume()
    suspend fun cancel()
}

/**
 * Streaming download engine with range resume (spec §3). Implementation
 * (core-download) owns the state machine, WorkManager worker, temp-file
 * hygiene, and checksum verification. The app injects a fake during v0.0.
 *
 * v0.1 runs a single-task serial queue (spec §3.1): [enqueue] queues; the task
 * starts when the slot is free. [observeAll] exposes the queue for the
 * downloads screen.
 */
interface DownloadEngine {
    suspend fun enqueue(pkg: OtaPackage): DownloadTask
    fun observeAll(): Flow<List<DownloadTask>>
}
