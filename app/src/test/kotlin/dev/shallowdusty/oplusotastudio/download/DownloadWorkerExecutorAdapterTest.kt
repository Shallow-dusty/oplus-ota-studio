package dev.shallowdusty.oplusotastudio.download

import dev.shallowdusty.oplusotastudio.core.model.DownloadState
import dev.shallowdusty.oplusotastudio.core.model.OtaErrorCategory
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
}
