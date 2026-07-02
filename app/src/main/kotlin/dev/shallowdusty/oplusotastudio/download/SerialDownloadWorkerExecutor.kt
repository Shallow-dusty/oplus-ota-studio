package dev.shallowdusty.oplusotastudio.download

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class SerialDownloadWorkerExecutor(
    private val delegate: DownloadWorkerExecutor,
) : DownloadWorkerExecutor {
    private val mutex = Mutex()

    override suspend fun execute(taskId: String): DownloadWorkerExecutionResult =
        mutex.withLock {
            delegate.execute(taskId)
        }

    override fun stop(taskId: String) {
        delegate.stop(taskId)
    }
}
