package dev.shallowdusty.oplusotastudio.feature.downloads

import android.content.ClipData
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.shallowdusty.oplusotastudio.core.model.DownloadState
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import kotlinx.coroutines.launch

/**
 * The downloads screen (spec §5 step 5-6, §6). Renders the engine's task queue;
 * each row maps a [DownloadState] branch to its own layout and actions.
 *
 * [factory] lets the app inject a [DownloadsViewModel] wired to AppGraph.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DownloadsScreen(
    factory: () -> DownloadsViewModel,
) {
    val viewModel: DownloadsViewModel = viewModel(factory = viewModelFactory { initializer { factory() } })
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        topBar = { TopAppBar(title = { Text("Downloads") }) },
    ) { padding ->
        if (state.rows.isEmpty() && state.historyRows.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text("No downloads yet.", style = MaterialTheme.typography.bodyMedium)
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (state.rows.isNotEmpty()) {
                    item {
                        SectionTitle("Active downloads")
                    }
                    items(state.rows, key = { it.taskId }) { row ->
                        DownloadRowCard(
                            row = row,
                            onPause = viewModel::pause,
                            onResume = viewModel::resume,
                            onCancel = viewModel::cancel,
                        )
                    }
                }
                if (state.historyRows.isNotEmpty()) {
                    item {
                        SectionTitle("Package history")
                    }
                    items(state.historyRows, key = { it.id }) { row ->
                        HistoryRowCard(row)
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(text, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
}

@Composable
private fun DownloadRowCard(
    row: DownloadRow,
    onPause: (String) -> Unit,
    onResume: (String) -> Unit,
    onCancel: (String) -> Unit,
) {
    Column(Modifier.fillMaxWidth()) {
        Text(
            text = row.taskId.takeLast(8),
            style = MaterialTheme.typography.labelMedium,
            fontFamily = FontFamily.Monospace,
        )
        Spacer(Modifier.height(4.dp))
        when (val s = row.state) {
            DownloadState.Queued -> StateLabel("Queued")
            is DownloadState.Running -> RunningContent(
                state = s,
                taskId = row.taskId,
                onPause = onPause,
                onCancel = onCancel,
            )
            is DownloadState.Paused -> PausedContent(
                state = s,
                taskId = row.taskId,
                onResume = onResume,
                onCancel = onCancel,
            )
            is DownloadState.Retrying -> RetryingContent(s)
            DownloadState.Verifying -> VerifyingContent()
            DownloadState.Verified -> StateLabel("Verified", color = MaterialTheme.colorScheme.primary)
            DownloadState.Unverified -> UnverifiedContent()
            DownloadState.Canceled -> StateLabel("Canceled", color = MaterialTheme.colorScheme.onSurfaceVariant)
            is DownloadState.Failed -> FailedContent(s)
        }
    }
}

@Composable
private fun HistoryRowCard(row: HistoryRow) {
    val clipboard = LocalClipboard.current
    val coroutineScope = rememberCoroutineScope()
    Column(Modifier.fillMaxWidth()) {
        Text(row.packageName, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
        Spacer(Modifier.height(4.dp))
        Text(
            "${formatBytes(row.packageSize)} · ${row.sourceHost} · ${row.evidenceLabel}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        row.localFilePath?.let { path ->
            Spacer(Modifier.height(4.dp))
            Text(
                path,
                style = MaterialTheme.typography.bodySmall,
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(
                onClick = {
                    coroutineScope.launch {
                        clipboard.setClipEntry(
                            ClipEntry(ClipData.newPlainText("OTA package link", row.downloadUrl)),
                        )
                    }
                },
                modifier = Modifier.weight(1f),
            ) {
                Icon(Icons.Filled.ContentCopy, contentDescription = null)
                Spacer(Modifier.size(8.dp))
                Text("Copy link")
            }
            row.localFilePath?.let { path ->
                OutlinedButton(
                    onClick = {
                        coroutineScope.launch {
                            clipboard.setClipEntry(
                                ClipEntry(ClipData.newPlainText("Downloaded OTA path", path)),
                            )
                        }
                    },
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(Icons.Filled.ContentCopy, contentDescription = null)
                    Spacer(Modifier.size(8.dp))
                    Text("Copy path")
                }
            }
        }
    }
}

@Composable
private fun StateLabel(text: String, color: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.onSurface) {
    Text(text, style = MaterialTheme.typography.bodyMedium, color = color, fontWeight = FontWeight.Medium)
}

@Composable
private fun RunningContent(
    state: DownloadState.Running,
    taskId: String,
    onPause: (String) -> Unit,
    onCancel: (String) -> Unit,
) {
    val target = state.targetSize
    val progress = if (target != null && target > 0) {
        (state.downloadedBytes.toFloat() / target).coerceIn(0f, 1f)
    } else null
    if (progress != null) {
        LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
    } else {
        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
    }
    Spacer(Modifier.height(4.dp))
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text("${formatBytes(state.downloadedBytes)} / ${target?.let(::formatBytes) ?: "?"}", style = MaterialTheme.typography.bodySmall)
        Text(state.speedBytesPerSec?.let { "${formatBytes(it)}/s" } ?: "—", style = MaterialTheme.typography.bodySmall)
    }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(onClick = { onPause(taskId) }, modifier = Modifier.weight(1f)) { Text("Pause") }
        TextButton(onClick = { onCancel(taskId) }, modifier = Modifier.weight(1f)) { Text("Cancel") }
    }
}

@Composable
private fun PausedContent(
    state: DownloadState.Paused,
    taskId: String,
    onResume: (String) -> Unit,
    onCancel: (String) -> Unit,
) {
    StateLabel("Paused (${state.reason.name.lowercase()})", color = MaterialTheme.colorScheme.tertiary)
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(onClick = { onResume(taskId) }, modifier = Modifier.weight(1f)) { Text("Resume") }
        TextButton(onClick = { onCancel(taskId) }, modifier = Modifier.weight(1f)) { Text("Cancel") }
    }
}

@Composable
private fun RetryingContent(state: DownloadState.Retrying) {
    StateLabel("Retrying (${state.attempt}/${state.maxAttempts}) — ${state.category.name.lowercase()}", color = MaterialTheme.colorScheme.tertiary)
    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
}

@Composable
private fun VerifyingContent() {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        CircularProgressIndicator(modifier = Modifier.height(16.dp))
        Text("Verifying checksum…", style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun UnverifiedContent() {
    StateLabel("Downloaded, not verified", color = MaterialTheme.colorScheme.tertiary)
    Text(
        "No checksum was provided. Transfer integrity is unknown.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun FailedContent(state: DownloadState.Failed) {
    StateLabel("Failed — ${state.category.name.lowercase()}", color = MaterialTheme.colorScheme.error)
    if (state.retriesRemaining > 0) {
        Text("${state.retriesRemaining} retries remaining", style = MaterialTheme.typography.bodySmall)
    }
    state.raw?.let {
        Text(it, style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

private fun formatBytes(bytes: Long): String {
    val mb = bytes / 1_000_000.0
    return when {
        mb >= 1000 -> "%.2f GB".format(mb / 1000)
        mb >= 1 -> "%.1f MB".format(mb)
        else -> "%d B".format(bytes)
    }
}
