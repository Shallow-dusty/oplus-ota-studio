package dev.shallowdusty.oplusotastudio.download

import dev.shallowdusty.oplusotastudio.core.model.DownloadState
import dev.shallowdusty.oplusotastudio.core.model.OtaErrorCategory
import java.io.IOException
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class DownloadWorkerExecutorAdapterTest {

    @Test
    fun `returns success when stored task verifies`() = runTest {
        val executor = DownloadWorkerExecutorAdapter {
            DownloadState.Verified
        }

        assertEquals(
            DownloadWorkerExecutionResult.Succeeded,
            executor.execute("task-1"),
        )
    }

    @Test
    fun `returns success when stored task is unverified`() = runTest {
        val executor = DownloadWorkerExecutorAdapter {
            DownloadState.Unverified
        }

        assertEquals(
            DownloadWorkerExecutionResult.Succeeded,
            executor.execute("task-1"),
        )
    }

    @Test
    fun `returns retry when stored task reaches retriable state`() = runTest {
        val executor = DownloadWorkerExecutorAdapter {
            DownloadState.Retrying(
                attempt = 1,
                maxAttempts = 3,
                category = OtaErrorCategory.Network,
            )
        }

        assertEquals(
            DownloadWorkerExecutionResult.Retry,
            executor.execute("task-1"),
        )
    }

    @Test
    fun `returns retry when stored task remains automatically paused`() = runTest {
        val executor = DownloadWorkerExecutorAdapter {
            DownloadState.Paused(DownloadState.Paused.PauseReason.BatteryLow)
        }

        assertEquals(
            DownloadWorkerExecutionResult.Retry,
            executor.execute("task-1"),
        )
    }

    @Test
    fun `returns failure when stored task remains user paused`() = runTest {
        val executor = DownloadWorkerExecutorAdapter {
            DownloadState.Paused(DownloadState.Paused.PauseReason.User)
        }

        assertEquals(
            DownloadWorkerExecutionResult.Failed,
            executor.execute("task-1"),
        )
    }

    @Test
    fun `returns failure when stored task fails terminally`() = runTest {
        val executor = DownloadWorkerExecutorAdapter {
            DownloadState.Failed(
                category = OtaErrorCategory.ChecksumMismatch,
                retriesRemaining = 0,
                raw = "bad checksum",
            )
        }

        assertEquals(
            DownloadWorkerExecutionResult.Failed,
            executor.execute("task-1"),
        )
    }

    @Test
    fun `returns retry when stored task fails with retriable attempts remaining`() = runTest {
        val executor = DownloadWorkerExecutorAdapter {
            DownloadState.Failed(
                category = OtaErrorCategory.Network,
                retriesRemaining = 1,
                raw = "timeout",
            )
        }

        assertEquals(
            DownloadWorkerExecutionResult.Retry,
            executor.execute("task-1"),
        )
    }

    @Test
    fun `returns retry when stored task execution has network io exception`() = runTest {
        val executor = DownloadWorkerExecutorAdapter {
            throw IOException("socket closed")
        }

        assertEquals(
            DownloadWorkerExecutionResult.Retry,
            executor.execute("task-1"),
        )
    }

    @Test
    fun `returns failure when stored task execution has non-io exception`() = runTest {
        val executor = DownloadWorkerExecutorAdapter {
            throw IllegalStateException("bad stored task")
        }

        assertEquals(
            DownloadWorkerExecutionResult.Failed,
            executor.execute("task-1"),
        )
    }

    @Test
    fun `stop delegates to stored task stop callback`() {
        val stopped = mutableListOf<String>()
        val executor = DownloadWorkerExecutorAdapter(
            executeStoredTask = { DownloadState.Verified },
            stopStoredTask = { stopped += it },
        )

        executor.stop("task-1")

        assertEquals(listOf("task-1"), stopped)
    }
}
