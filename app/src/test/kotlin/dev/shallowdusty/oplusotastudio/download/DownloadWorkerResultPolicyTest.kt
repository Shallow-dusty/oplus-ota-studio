package dev.shallowdusty.oplusotastudio.download

import androidx.work.ListenableWorker
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class DownloadWorkerResultPolicyTest {

    @Test
    fun `succeeds when execution backend completes task`() {
        val result = DownloadWorkerResultPolicy.resultFor(
            taskId = "task-1",
            executionResult = DownloadWorkerExecutionResult.Succeeded,
        )

        assertEquals(ListenableWorker.Result.success(), result)
    }

    @Test
    fun `retries when execution backend asks for retry`() {
        val result = DownloadWorkerResultPolicy.resultFor(
            taskId = "task-1",
            executionResult = DownloadWorkerExecutionResult.Retry,
        )

        assertEquals(ListenableWorker.Result.retry(), result)
    }

    @Test
    fun `fails when task id is blank`() {
        val result = DownloadWorkerResultPolicy.resultFor(
            taskId = " ",
            executionResult = DownloadWorkerExecutionResult.Succeeded,
        )

        assertEquals(ListenableWorker.Result.failure(), result)
    }

    @Test
    fun `fails when execution backend is not available`() {
        val result = DownloadWorkerResultPolicy.resultFor(
            taskId = "task-1",
            executionResult = null,
        )

        assertEquals(ListenableWorker.Result.failure(), result)
    }

    @Test
    fun `fails when execution backend reports failure`() {
        val result = DownloadWorkerResultPolicy.resultFor(
            taskId = "task-1",
            executionResult = DownloadWorkerExecutionResult.Failed,
        )

        assertEquals(ListenableWorker.Result.failure(), result)
    }
}
