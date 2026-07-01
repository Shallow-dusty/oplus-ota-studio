package dev.shallowdusty.oplusotastudio.feature.lookup

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Search
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.shallowdusty.oplusotastudio.core.model.OtaProfile
import dev.shallowdusty.oplusotastudio.core.model.OtaRegion
import dev.shallowdusty.oplusotastudio.core.model.isLookupReady
import kotlinx.coroutines.launch

/**
 * The lookup screen (spec §5, §6). Renders every [LookupUiState] branch
 * explicitly so each state has its own copy and layout.
 *
 * [factory] lets the app inject a [LookupViewModel] wired to the AppGraph
 * services; tests construct the ViewModel directly.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LookupScreen(
    factory: () -> LookupViewModel,
    beforeDownload: ((() -> Unit) -> Unit) = { action -> action() },
    onDownloadQueued: () -> Unit = {},
) {
    val viewModel: LookupViewModel = viewModel(factory = viewModelFactory { initializer { factory() } })
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        topBar = { TopAppBar(title = { Text(stringResource(R.string.lookup_title)) }) },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when (val s = state) {
                LookupUiState.Detecting -> DetectingContent()
                is LookupUiState.Ready -> ReadyContent(s, viewModel::lookup, viewModel::updateProfile)
                is LookupUiState.PrivacyDisclosureRequired -> PrivacyDisclosureContent(
                    state = s,
                    onContinue = viewModel::acceptPrivacyDisclosureAndLookup,
                    onBack = viewModel::reset,
                )
                LookupUiState.Querying -> QueryingContent()
                is LookupUiState.PackageFound -> PackageFoundContent(
                    pkg = s.pkg,
                    liveLookupExperimental = s.liveLookupExperimental,
                    onDownload = { pkg ->
                        beforeDownload {
                            viewModel.enqueueDownload(pkg)
                            onDownloadQueued()
                        }
                    },
                    onReset = viewModel::reset,
                )
                LookupUiState.NoUpdate -> NoUpdateContent(viewModel::reset)
                is LookupUiState.Error -> ErrorContent(s, viewModel::reset)
            }
        }
    }
}

@Composable
private fun PrivacyDisclosureContent(
    state: LookupUiState.PrivacyDisclosureRequired,
    onContinue: () -> Unit,
    onBack: () -> Unit,
) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(stringResource(R.string.lookup_before_title), style = MaterialTheme.typography.headlineSmall)
        Text(
            stringResource(R.string.lookup_privacy_body),
            style = MaterialTheme.typography.bodyMedium,
        )
        SummaryRow(stringResource(R.string.lookup_label_model), state.profile.model)
        SummaryRow(stringResource(R.string.lookup_label_region), state.profile.region.name)
        SummaryRow(stringResource(R.string.lookup_label_ota_version), state.profile.otaVersion)
        Text(
            stringResource(R.string.lookup_privacy_no_serial),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Button(onClick = onContinue, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.lookup_continue))
        }
        OutlinedButton(onClick = onBack, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.lookup_back))
        }
    }
}

@Composable
private fun DetectingContent() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator()
            Spacer(Modifier.height(12.dp))
            Text(stringResource(R.string.lookup_detecting), style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun QueryingContent() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator()
            Spacer(Modifier.height(12.dp))
            Text(stringResource(R.string.lookup_querying), style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun ReadyContent(
    state: LookupUiState.Ready,
    onLookup: () -> Unit,
    onProfileChange: (OtaProfile) -> Unit,
) {
    val profile = state.profile
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (state.device != null) {
            DeviceSummary(state.device)
            HorizontalDivider()
        }
        Text(stringResource(R.string.lookup_profile_title), style = MaterialTheme.typography.titleMedium)
        OutlinedTextField(
            value = profile.model,
            onValueChange = { onProfileChange(profile.copy(model = it)) },
            label = { Text(stringResource(R.string.lookup_label_model)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = profile.otaVersion,
            onValueChange = { onProfileChange(profile.copy(otaVersion = it)) },
            label = { Text(stringResource(R.string.lookup_label_ota_version_build)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        RegionSelector(
            region = profile.region,
            onRegionChange = { onProfileChange(profile.copy(region = it)) },
        )
        AdvancedHostOverride(
            profile = profile,
            onProfileChange = onProfileChange,
        )
        Button(
            onClick = onLookup,
            enabled = profile.isLookupReady,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Icon(Icons.Filled.Search, contentDescription = null)
            Spacer(Modifier.size(8.dp))
            Text(stringResource(R.string.lookup_profile_action))
        }
    }
}

@Composable
private fun RegionSelector(
    region: OtaRegion,
    onRegionChange: (OtaRegion) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(stringResource(R.string.lookup_label_region), style = MaterialTheme.typography.titleSmall)
        Box {
            OutlinedButton(onClick = { expanded = true }, modifier = Modifier.fillMaxWidth()) {
                Text(region.name)
            }
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                OtaRegion.entries.forEach { option ->
                    DropdownMenuItem(
                        text = { Text(option.name) },
                        onClick = {
                            expanded = false
                            onRegionChange(option)
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun AdvancedHostOverride(
    profile: OtaProfile,
    onProfileChange: (OtaProfile) -> Unit,
) {
    var expanded by remember(profile.hostOverride) {
        mutableStateOf(!profile.hostOverride.isNullOrBlank())
    }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(
                checked = expanded,
                onCheckedChange = { checked ->
                    expanded = checked
                    if (!checked) {
                        onProfileChange(profile.copy(hostOverride = null))
                    }
                },
            )
            Text(stringResource(R.string.lookup_advanced_host_override), style = MaterialTheme.typography.bodyMedium)
        }
        if (expanded) {
            OutlinedTextField(
                value = profile.hostOverride.orEmpty(),
                onValueChange = { onProfileChange(profile.copy(hostOverride = it)) },
                label = { Text(stringResource(R.string.lookup_label_ota_host)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun DeviceSummary(device: dev.shallowdusty.oplusotastudio.core.model.DeviceProfile) {
    Column {
        Text(stringResource(R.string.lookup_detected_device), style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(4.dp))
        SummaryRow(stringResource(R.string.lookup_label_model), device.model)
        SummaryRow(stringResource(R.string.lookup_label_marketing_name), device.marketingName)
        SummaryRow(stringResource(R.string.lookup_label_ota_version), device.otaVersion)
        SummaryRow(stringResource(R.string.lookup_label_android), device.androidVersion)
        SummaryRow(stringResource(R.string.lookup_label_region), device.region?.name)
        if (device.incomplete) {
            Text(
                stringResource(R.string.lookup_detection_incomplete),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }
    }
}

@Composable
private fun SummaryRow(label: String, value: String?) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(
            value ?: "—",
            style = MaterialTheme.typography.bodyMedium,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Medium,
        )
    }
}

@Composable
private fun PackageFoundContent(
    pkg: dev.shallowdusty.oplusotastudio.core.model.OtaPackage,
    liveLookupExperimental: Boolean,
    onDownload: (dev.shallowdusty.oplusotastudio.core.model.OtaPackage) -> Unit,
    onReset: () -> Unit,
) {
    val clipboard = LocalClipboard.current
    val coroutineScope = rememberCoroutineScope()
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(stringResource(R.string.lookup_update_available), style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.primary)
        SummaryRow(stringResource(R.string.lookup_label_version), pkg.versionName)
        SummaryRow(stringResource(R.string.lookup_label_type), pkg.type)
        SummaryRow(stringResource(R.string.lookup_label_size), formatBytes(pkg.sizeBytes))
        SummaryRow(stringResource(R.string.lookup_label_source), pkg.sourceHost)
        SummaryRow(stringResource(R.string.lookup_label_evidence), pkg.evidenceLevel.stableId)
        if (liveLookupExperimental) {
            Text(
                stringResource(R.string.lookup_experimental_notice),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }
        SummaryRow(stringResource(R.string.lookup_label_md5), pkg.md5)
        SummaryRow(stringResource(R.string.lookup_label_sha256), pkg.sha256)
        Text(
            stringResource(R.string.lookup_verification_scope),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        val notes = pkg.releaseNotes
        if (notes != null) {
            Spacer(Modifier.height(8.dp))
            Text(stringResource(R.string.lookup_release_notes), style = MaterialTheme.typography.titleMedium)
            Text(notes, style = MaterialTheme.typography.bodyMedium)
        }
        Spacer(Modifier.height(16.dp))
        Button(onClick = { onDownload(pkg) }, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Filled.CloudDownload, contentDescription = null)
            Spacer(Modifier.size(8.dp))
            Text(stringResource(R.string.lookup_download_package))
        }
        val clipboardPackageLinkLabel = stringResource(R.string.lookup_clipboard_package_link)
        OutlinedButton(
            onClick = {
                coroutineScope.launch {
                    clipboard.setClipEntry(
                        ClipEntry(
                            ClipData.newPlainText(clipboardPackageLinkLabel, pkg.downloadUrl),
                        ),
                    )
                }
            },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Icon(Icons.Filled.ContentCopy, contentDescription = null)
            Spacer(Modifier.size(8.dp))
            Text(stringResource(R.string.lookup_copy_link))
        }
        OutlinedButton(onClick = onReset, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.lookup_back_to_lookup))
        }
    }
}

@Composable
private fun NoUpdateContent(onReset: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(stringResource(R.string.lookup_up_to_date), style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.primary)
        Text(stringResource(R.string.lookup_no_update), style = MaterialTheme.typography.bodyMedium)
        OutlinedButton(onClick = onReset, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.lookup_back_to_lookup)) }
    }
}

@Composable
private fun ErrorContent(state: LookupUiState.Error, onReset: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(stringResource(R.string.lookup_failed), style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.error)
        Text(categoryLabel(state.category), style = MaterialTheme.typography.bodyMedium)
        if (state.raw != null) {
            Text(
                state.raw,
                style = MaterialTheme.typography.bodySmall,
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        OutlinedButton(onClick = onReset, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.lookup_back_to_lookup)) }
    }
}

@Composable
private fun categoryLabel(c: dev.shallowdusty.oplusotastudio.core.model.OtaErrorCategory): String = when (c) {
    dev.shallowdusty.oplusotastudio.core.model.OtaErrorCategory.Network -> stringResource(R.string.lookup_error_network)
    dev.shallowdusty.oplusotastudio.core.model.OtaErrorCategory.Server -> stringResource(R.string.lookup_error_server)
    dev.shallowdusty.oplusotastudio.core.model.OtaErrorCategory.Malformed -> stringResource(R.string.lookup_error_malformed)
    dev.shallowdusty.oplusotastudio.core.model.OtaErrorCategory.Device -> stringResource(R.string.lookup_error_device)
    dev.shallowdusty.oplusotastudio.core.model.OtaErrorCategory.File -> stringResource(R.string.lookup_error_file)
    dev.shallowdusty.oplusotastudio.core.model.OtaErrorCategory.ChecksumMismatch -> stringResource(R.string.lookup_error_checksum)
    dev.shallowdusty.oplusotastudio.core.model.OtaErrorCategory.Unknown -> stringResource(R.string.lookup_error_unknown)
}

private fun formatBytes(bytes: Long): String {
    val mb = bytes / 1_000_000.0
    return if (mb >= 1000) "%.2f GB".format(mb / 1000) else "%.1f MB".format(mb)
}
