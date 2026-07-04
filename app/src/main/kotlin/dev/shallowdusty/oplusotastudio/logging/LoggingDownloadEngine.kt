package dev.shallowdusty.oplusotastudio.logging

import dev.shallowdusty.oplusotastudio.core.model.DownloadEngine
import dev.shallowdusty.oplusotastudio.core.model.DownloadState
import dev.shallowdusty.oplusotastudio.core.model.DownloadTask
import dev.shallowdusty.oplusotastudio.core.model.OtaPackage
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach

class LoggingDownloadEngine(
    private val delegate: DownloadEngine,
    private val logger: AppLogger,
) : DownloadEngine {

    override suspend fun enqueue(pkg: OtaPackage): DownloadTask {
        logger.info(
            tag = Tag,
            message = "enqueue requested version=${pkg.versionName} host=${pkg.sourceHost} sizeBytes=${pkg.sizeBytes}",
        )
        val task = delegate.enqueue(pkg)
        logger.info(tag = Tag, message = "enqueue accepted taskId=${task.taskId}")
        return LoggingDownloadTask(delegate = task, logger = logger)
    }

    override fun observeAll(): Flow<List<DownloadTask>> =
        delegate.observeAll().map { tasks ->
            tasks.map { task -> LoggingDownloadTask(delegate = task, logger = logger) }
        }

    private class LoggingDownloadTask(
        private val delegate: DownloadTask,
        private val logger: AppLogger,
    ) : DownloadTask {
        override val taskId: String = delegate.taskId

        override val state: Flow<DownloadState> =
            delegate.state.onEach { state -> logger.logState(taskId, state) }

        override suspend fun pause() {
            logger.info(tag = Tag, message = "pause requested taskId=$taskId")
            delegate.pause()
        }

        override suspend fun resume() {
            logger.info(tag = Tag, message = "resume requested taskId=$taskId")
            delegate.resume()
        }

        override suspend fun cancel() {
            logger.info(tag = Tag, message = "cancel requested taskId=$taskId")
            delegate.cancel()
        }
    }

    private companion object {
        const val Tag = "Download"

        fun AppLogger.logState(taskId: String, state: DownloadState) {
            when (state) {
                DownloadState.Queued -> info(tag = Tag, message = "state taskId=$taskId state=Queued")
                is DownloadState.Running -> info(
                    tag = Tag,
                    message = "state taskId=$taskId state=Running " +
                        "downloadedBytes=${state.downloadedBytes} targetSize=${state.targetSize}",
                )
                is DownloadState.Paused -> info(
                    tag = Tag,
                    message = "state taskId=$taskId state=Paused reason=${state.reason}",
                )
                is DownloadState.Retrying -> warn(
                    tag = Tag,
                    message = "state taskId=$taskId state=Retrying " +
                        "attempt=${state.attempt} maxAttempts=${state.maxAttempts} category=${state.category}",
                )
                DownloadState.Verifying -> info(tag = Tag, message = "state taskId=$taskId state=Verifying")
                DownloadState.Verified -> info(tag = Tag, message = "state taskId=$taskId state=Verified")
                DownloadState.Unverified -> warn(tag = Tag, message = "state taskId=$taskId state=Unverified")
                DownloadState.Canceled -> info(tag = Tag, message = "state taskId=$taskId state=Canceled")
                is DownloadState.Failed -> error(
                    tag = Tag,
                    message = "state taskId=$taskId state=Failed " +
                        "category=${state.category} retriesRemaining=${state.retriesRemaining}",
                )
            }
        }
    }
}
