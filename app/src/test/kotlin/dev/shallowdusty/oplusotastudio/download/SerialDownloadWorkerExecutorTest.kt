package dev.shallowdusty.oplusotastudio.download

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test

class SerialDownloadWorkerExecutorTest {

    @Test
    @OptIn(ExperimentalCoroutinesApi::class)
    fun `runs worker executions one at a time`() = runTest {
        val firstEntered = CompletableDeferred<Unit>()
        val releaseFirst = CompletableDeferred<Unit>()
        val secondEntered = CompletableDeferred<Unit>()
        var runningCount = 0
        var maxRunningCount = 0
        val executor = SerialDownloadWorkerExecutor(
            object : DownloadWorkerExecutor {
                override suspend fun execute(taskId: String): DownloadWorkerExecutionResult {
                    runningCount += 1
                    maxRunningCount = maxOf(maxRunningCount, runningCount)
                    if (taskId == "task-1") {
                        firstEntered.complete(Unit)
                        releaseFirst.await()
                    } else {
                        secondEntered.complete(Unit)
                    }
                    runningCount -= 1
                    return DownloadWorkerExecutionResult.Succeeded
                }
            },
        )

        val first = async { executor.execute("task-1") }
        firstEntered.await()
        val second = async { executor.execute("task-2") }

        runCurrent()

        assertFalse(secondEntered.isCompleted)

        releaseFirst.complete(Unit)

        assertEquals(DownloadWorkerExecutionResult.Succeeded, first.await())
        assertEquals(DownloadWorkerExecutionResult.Succeeded, second.await())
        assertEquals(1, maxRunningCount)
    }
}
