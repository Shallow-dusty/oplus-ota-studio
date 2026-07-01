package dev.shallowdusty.oplusotastudio.feature.downloads

import dev.shallowdusty.oplusotastudio.core.model.DownloadEngine
import dev.shallowdusty.oplusotastudio.core.model.DownloadState
import dev.shallowdusty.oplusotastudio.core.model.DownloadTask
import dev.shallowdusty.oplusotastudio.core.model.HistoryEntry
import dev.shallowdusty.oplusotastudio.core.model.OtaEvidenceLevel
import dev.shallowdusty.oplusotastudio.core.model.OtaPackage
import dev.shallowdusty.oplusotastudio.core.model.OtaRegion
import dev.shallowdusty.oplusotastudio.core.model.PackageRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flowOf
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

@OptIn(ExperimentalCoroutinesApi::class)
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
    fun `unverified state is rendered`() = runTest {
        val engine = FakeDownloadEngine()
        val task = engine.enqueueNow(samplePackage().copy(md5 = null, sha256 = null)) as FakeDownloadTask
        val vm = DownloadsViewModel(engine)
        advanceUntilIdle()
        task.emit(DownloadState.Unverified)
        advanceUntilIdle()
        assertEquals(DownloadState.Unverified, vm.uiState.value.rows.first().state)
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

    @Test
    fun `removed task disappears from rows and stops updating`() = runTest {
        val engine = FakeDownloadEngine()
        val task = engine.enqueueNow(samplePackage()) as FakeDownloadTask
        val vm = DownloadsViewModel(engine)
        advanceUntilIdle()

        engine.remove(task)
        advanceUntilIdle()
        task.emit(DownloadState.Running(100L, 1000L, null))
        advanceUntilIdle()

        assertTrue(vm.uiState.value.rows.isEmpty())
    }

    @Test
    fun `pause by task id forwards to matching task`() = runTest {
        val engine = FakeDownloadEngine()
        val task = engine.enqueueNow(samplePackage()) as FakeDownloadTask
        val vm = DownloadsViewModel(engine)
        advanceUntilIdle()

        vm.pause(task.taskId)
        advanceUntilIdle()

        assertEquals(1, task.pauseCalls)
        assertEquals(0, task.resumeCalls)
        assertEquals(0, task.cancelCalls)
    }

    @Test
    fun `resume and cancel by task id forward to matching task`() = runTest {
        val engine = FakeDownloadEngine()
        val task = engine.enqueueNow(samplePackage()) as FakeDownloadTask
        val vm = DownloadsViewModel(engine)
        advanceUntilIdle()

        vm.resume(task.taskId)
        vm.cancel(task.taskId)
        advanceUntilIdle()

        assertEquals(0, task.pauseCalls)
        assertEquals(1, task.resumeCalls)
        assertEquals(1, task.cancelCalls)
    }

    @Test
    fun `download action with unknown task id is ignored`() = runTest {
        val engine = FakeDownloadEngine()
        val task = engine.enqueueNow(samplePackage()) as FakeDownloadTask
        val vm = DownloadsViewModel(engine)
        advanceUntilIdle()

        vm.pause("missing")
        vm.resume("missing")
        vm.cancel("missing")
        advanceUntilIdle()

        assertEquals(0, task.pauseCalls)
        assertEquals(0, task.resumeCalls)
        assertEquals(0, task.cancelCalls)
    }

    @Test
    fun `lookup history appears as package rows with copyable links`() = runTest {
        val engine = FakeDownloadEngine()
        val repository = FakePackageRepository(
            history = listOf(
                sampleHistoryEntry(),
            ),
        )

        val vm = DownloadsViewModel(engine, repository)
        advanceUntilIdle()

        val historyRow = vm.uiState.value.historyRows.single()
        assertEquals("CPH2581_15.0.0.840(EX01).zip", historyRow.packageName)
        assertEquals("https://otagm.oppo.com/cph2581.zip", historyRow.downloadUrl)
        assertEquals("content://downloads/oplus/cph2581.zip", historyRow.localFilePath)
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

    private fun sampleHistoryEntry() = HistoryEntry(
        id = "history-1",
        profileModel = "CPH2581",
        profileRegion = OtaRegion.India,
        packageName = "CPH2581_15.0.0.840(EX01).zip",
        packageSize = 6_200_000_000L,
        sourceHost = "otagm.oppo.com",
        downloadUrl = "https://otagm.oppo.com/cph2581.zip",
        md5 = "abc",
        sha256 = null,
        releaseNotes = null,
        evidenceLevel = OtaEvidenceLevel.LiveVerified,
        lookedUpAtMs = 1_720_000_000_000L,
        downloadedAtMs = 1_720_000_100_000L,
        localFilePath = "content://downloads/oplus/cph2581.zip",
    )

    private class FakeDownloadEngine : DownloadEngine {
        private val tasks = mutableListOf<FakeDownloadTask>()
        private val observedTasks = MutableStateFlow<List<DownloadTask>>(emptyList())

        override suspend fun enqueue(pkg: OtaPackage): DownloadTask {
            val task = FakeDownloadTask()
            tasks.add(task)
            observedTasks.value = tasks.toList()
            return task
        }

        fun enqueueNow(pkg: OtaPackage): DownloadTask = kotlinx.coroutines.runBlocking { enqueue(pkg) }

        fun remove(task: FakeDownloadTask) {
            tasks.remove(task)
            observedTasks.value = tasks.toList()
        }

        override fun observeAll(): Flow<List<DownloadTask>> = observedTasks.asStateFlow()
    }

    private class FakeDownloadTask : DownloadTask {
        override val taskId: String = "fake-${System.nanoTime()}"
        private val _state = MutableSharedFlow<DownloadState>(replay = 1)
        override val state: Flow<DownloadState> = _state.asSharedFlow()
        var pauseCalls: Int = 0
            private set
        var resumeCalls: Int = 0
            private set
        var cancelCalls: Int = 0
            private set

        override suspend fun pause() {
            pauseCalls += 1
        }

        override suspend fun resume() {
            resumeCalls += 1
        }

        override suspend fun cancel() {
            cancelCalls += 1
        }

        suspend fun emit(state: DownloadState) {
            _state.emit(state)
        }
    }

    private class FakePackageRepository(
        private val history: List<HistoryEntry>,
    ) : PackageRepository {
        override suspend fun record(entry: HistoryEntry) = Unit

        override suspend fun markDownloaded(packageName: String, downloadedAtMs: Long, localFilePath: String) = Unit

        override fun observeHistory(): Flow<List<HistoryEntry>> = flowOf(history)
    }
}
