package dev.shallowdusty.oplusotastudio.feature.downloads

import dev.shallowdusty.oplusotastudio.core.model.DownloadEngine
import dev.shallowdusty.oplusotastudio.core.model.DownloadState
import dev.shallowdusty.oplusotastudio.core.model.DownloadTask
import dev.shallowdusty.oplusotastudio.core.model.OtaPackage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class DownloadsViewModelTest {

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @org.junit.jupiter.api.AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `empty queue yields empty rows`() = runTest {
        val engine = FakeDownloadEngine()
        val vm = DownloadsViewModel(engine)
        advanceUntilIdle()
        assertTrue(vm.uiState.value.rows.isEmpty())
    }

    @Test
    fun `enqueued task appears as a Queued row`() = runTest {
        val engine = FakeDownloadEngine()
        val task = engine.enqueueNow(samplePackage()) as FakeDownloadTask
        val vm = DownloadsViewModel(engine)
        advanceUntilIdle()
        assertEquals(1, vm.uiState.value.rows.size)
        assertEquals(DownloadState.Queued, vm.uiState.value.rows.first().state)
    }

    @Test
    fun `running progress is reflected in the row`() = runTest {
        val engine = FakeDownloadEngine()
        val task = engine.enqueueNow(samplePackage()) as FakeDownloadTask
        val vm = DownloadsViewModel(engine)
        advanceUntilIdle()
        task.emit(DownloadState.Running(1_000_000L, 3_500_000_000L, 5_000_000L))
        advanceUntilIdle()
        val row = vm.uiState.value.rows.first()
        assertTrue(row.state is DownloadState.Running)
        val running = row.state as DownloadState.Running
        assertEquals(1_000_000L, running.downloadedBytes)
        assertEquals(5_000_000L, running.speedBytesPerSec)
    }

    @Test
    fun `verified state is rendered`() = runTest {
        val engine = FakeDownloadEngine()
        val task = engine.enqueueNow(samplePackage()) as FakeDownloadTask
        val vm = DownloadsViewModel(engine)
        advanceUntilIdle()
        task.emit(DownloadState.Verified)
        advanceUntilIdle()
        assertEquals(DownloadState.Verified, vm.uiState.value.rows.first().state)
    }

    @Test
    fun `failed with checksum mismatch is terminal and carries raw`() = runTest {
        val engine = FakeDownloadEngine()
        val task = engine.enqueueNow(samplePackage()) as FakeDownloadTask
        val vm = DownloadsViewModel(engine)
        advanceUntilIdle()
        task.emit(
            DownloadState.Failed(
                category = dev.shallowdusty.oplusotastudio.core.model.OtaErrorCategory.ChecksumMismatch,
                retriesRemaining = 0,
                raw = "expected abc, got def",
            ),
        )
        advanceUntilIdle()
        val row = vm.uiState.value.rows.first()
        assertTrue(row.state is DownloadState.Failed)
        val failed = row.state as DownloadState.Failed
        assertEquals(dev.shallowdusty.oplusotastudio.core.model.OtaErrorCategory.ChecksumMismatch, failed.category)
        assertEquals(0, failed.retriesRemaining)
        assertEquals("expected abc, got def", failed.raw)
    }

    @Test
    fun `multiple tasks each tracked independently`() = runTest {
        val engine = FakeDownloadEngine()
        val t1 = engine.enqueueNow(samplePackage()) as FakeDownloadTask
        val t2 = engine.enqueueNow(samplePackage().copy(versionName = "v2")) as FakeDownloadTask
        val vm = DownloadsViewModel(engine)
        advanceUntilIdle()
        t1.emit(DownloadState.Running(100L, 1000L, null))
        t2.emit(DownloadState.Paused(DownloadState.Paused.PauseReason.NetworkLost))
        advanceUntilIdle()
        assertEquals(2, vm.uiState.value.rows.size)
    }

    private fun samplePackage() = OtaPackage(
        versionName = "12.0.0.0.LE28AA",
        type = "full",
        sizeBytes = 3_500_000_000L,
        sourceHost = "otagm.oppo.com",
        downloadUrl = "https://otagm.oppo.com/pkg.zip",
        md5 = "abc",
        sha256 = null,
    )

    private class FakeDownloadEngine : DownloadEngine {
        private val tasks = mutableListOf<FakeDownloadTask>()
        override suspend fun enqueue(pkg: OtaPackage): DownloadTask {
            val task = FakeDownloadTask()
            tasks.add(task)
            return task
        }

        fun enqueueNow(pkg: OtaPackage): DownloadTask = kotlinx.coroutines.runBlocking { enqueue(pkg) }

        override fun observeAll(): Flow<List<DownloadTask>> = flow { emit(tasks.toList()) }
    }

    private class FakeDownloadTask : DownloadTask {
        override val taskId: String = "fake-${System.nanoTime()}"
        private val _state = MutableSharedFlow<DownloadState>(replay = 1)
        override val state: Flow<DownloadState> = _state.asSharedFlow()
        override suspend fun pause() {}
        override suspend fun resume() {}
        override suspend fun cancel() {}

        suspend fun emit(state: DownloadState) {
            _state.emit(state)
        }
    }
}
