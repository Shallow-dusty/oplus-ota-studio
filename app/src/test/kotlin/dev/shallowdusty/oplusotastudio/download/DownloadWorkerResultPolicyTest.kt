package dev.shallowdusty.oplusotastudio.download

import androidx.work.ListenableWorker
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class DownloadWorkerResultPolicyTest {

    @Test
    fun `fails when execution backend is not wired yet`() {
        val result = DownloadWorkerResultPolicy.resultFor(
            taskId = "task-1",
            executionBackendAvailable = false,
        )

        assertEquals(ListenableWorker.Result.failure(), result)
    }

    @Test
    fun `fails when task id is blank`() {
        val result = DownloadWorkerResultPolicy.resultFor(
            taskId = " ",
            executionBackendAvailable = true,
        )

        assertEquals(ListenableWorker.Result.failure(), result)
    }
}
