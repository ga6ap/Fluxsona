package com.example.fluxsona.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.fluxsona.R
import com.example.fluxsona.ui.MusicViewModel
import com.example.fluxsona.ui.components.SkipIcon

import android.net.Uri
import android.provider.DocumentsContract
import android.content.Context
import android.content.Intent
import android.os.Build

@Composable
fun SettingsScreen(viewModel: MusicViewModel) {
    val context = LocalContext.current
    val stats by viewModel.appStats.collectAsState()
    val songs by viewModel.songs.collectAsState()
    val isRestoring = viewModel.isRestoring || viewModel.downloadQueue.any { it.existingSongId != null }

    LaunchedEffect(Unit) {
        viewModel.updateStats(context)
    }
    // Re-trigger stats update whenever songs change
    LaunchedEffect(songs) {
        viewModel.updateStats(context)
    }
    var showClearCacheDialog by remember { mutableStateOf(false) }
    var showDarkModeDialog by remember { mutableStateOf(false) }
    var showExportDialog by remember { mutableStateOf(false) }
    var clearAudio by remember { mutableStateOf(true) }
    var clearThumbnails by remember { mutableStateOf(true) }
    var clearLyrics by remember { mutableStateOf(true) }
    var clearFailed by remember { mutableStateOf(false) }

    var showRestoreDialog by remember { mutableStateOf(false) }
    var restoreAudio by remember { mutableStateOf(true) }
    var restoreThumbnails by remember { mutableStateOf(true) }
    var restoreLyrics by remember { mutableStateOf(true) }
    var clearFailedBeforeRestore by remember { mutableStateOf(false) }

    val importLauncher = rememberLauncherForActivityResult(
        contract = object : ActivityResultContracts.OpenDocument() {
            override fun createIntent(context: Context, input: Array<String>): Intent {
                return super.createIntent(context, input).apply {
                    // Try to point to Fluxsona Backups folder in Documents
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        val initialUri = Uri.parse("content://com.android.externalstorage.documents/document/primary%3ADocuments%2FFluxsona%20Backups")
                        putExtra(DocumentsContract.EXTRA_INITIAL_URI, initialUri)
                    }
                }
            }
        }
    ) { uri ->
        uri?.let { viewModel.importData(context, it) }
    }

    if (showClearCacheDialog) {
        AlertDialog(
            onDismissRequest = { showClearCacheDialog = false },
            title = { Text(stringResource(R.string.dialog_clear_cache_title)) },
            text = {
                Column {
                    Text(stringResource(R.string.dialog_clear_cache_text))
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                        modifier = Modifier.clickable { clearAudio = !clearAudio }
                    ) {
                        Checkbox(checked = clearAudio, onCheckedChange = { clearAudio = it })
                        Text(stringResource(R.string.dialog_clear_audio))
                    }
                    Row(
                        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                        modifier = Modifier.clickable { clearThumbnails = !clearThumbnails }
                    ) {
                        Checkbox(checked = clearThumbnails, onCheckedChange = { clearThumbnails = it })
                        Text(stringResource(R.string.dialog_clear_thumbnails))
                    }
                    Row(
                        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                        modifier = Modifier.clickable { clearLyrics = !clearLyrics }
                    ) {
                        Checkbox(checked = clearLyrics, onCheckedChange = { clearLyrics = it })
                        Text(stringResource(R.string.dialog_clear_lyrics))
                    }
                    Row(
                        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                        modifier = Modifier.clickable { clearFailed = !clearFailed }
                    ) {
                        Checkbox(checked = clearFailed, onCheckedChange = { clearFailed = it })
                        Text(stringResource(R.string.dialog_clear_failed))
                    }
                }
            },
            confirmButton = {
                TextButton(
                    enabled = clearAudio || clearThumbnails || clearLyrics || clearFailed,
                    onClick = {
                        viewModel.clearCache(context, clearAudio, clearThumbnails, clearLyrics, clearFailed)
                        showClearCacheDialog = false
                    }
                ) { Text(stringResource(R.string.dialog_clear)) }
            },
            dismissButton = {
                TextButton(onClick = { showClearCacheDialog = false }) { Text(stringResource(R.string.dialog_cancel)) }
            }
        )
    }

    if (showDarkModeDialog) {
        AlertDialog(
            onDismissRequest = { showDarkModeDialog = false },
            title = { Text(stringResource(R.string.dialog_appearance_title)) },
            text = {
                Column {
                    val options = listOf(
                        "system" to R.string.theme_system,
                        "light" to R.string.theme_light,
                        "dark" to R.string.theme_dark
                    )
                    options.forEach { (mode, labelRes) ->
                        Row(
                            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    viewModel.updateDarkMode(mode)
                                    showDarkModeDialog = false
                                }
                                .padding(vertical = 12.dp)
                        ) {
                            RadioButton(
                                selected = viewModel.darkMode == mode,
                                onClick = {
                                    viewModel.updateDarkMode(mode)
                                    showDarkModeDialog = false
                                }
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(stringResource(labelRes))
                        }
                    }
                }
            },
            confirmButton = {}
        )
    }

    if (showRestoreDialog) {
        AlertDialog(
            onDismissRequest = { showRestoreDialog = false },
            title = { Text(stringResource(R.string.menu_restore_data)) },
            text = {
                Column {
                    Text(stringResource(R.string.dialog_restore_data_multiple))
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                        modifier = Modifier.clickable { restoreAudio = !restoreAudio }
                    ) {
                        Checkbox(checked = restoreAudio, onCheckedChange = { restoreAudio = it })
                        Text(stringResource(R.string.dialog_clear_audio))
                    }
                    Row(
                        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                        modifier = Modifier.clickable { restoreThumbnails = !restoreThumbnails }
                    ) {
                        Checkbox(checked = restoreThumbnails, onCheckedChange = { restoreThumbnails = it })
                        Text(stringResource(R.string.dialog_clear_thumbnails))
                    }
                    Row(
                        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                        modifier = Modifier.clickable { restoreLyrics = !restoreLyrics }
                    ) {
                        Checkbox(checked = restoreLyrics, onCheckedChange = { restoreLyrics = it })
                        Text(stringResource(R.string.dialog_clear_lyrics))
                    }
                    Row(
                        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                        modifier = Modifier.clickable { clearFailedBeforeRestore = !clearFailedBeforeRestore }
                    ) {
                        Checkbox(checked = clearFailedBeforeRestore, onCheckedChange = { clearFailedBeforeRestore = it })
                        Text(stringResource(R.string.dialog_clear_failed))
                    }
                }
            },
            confirmButton = {
                TextButton(
                    enabled = restoreAudio || restoreThumbnails || restoreLyrics || clearFailedBeforeRestore,
                    onClick = {
                        if (clearFailedBeforeRestore) {
                            viewModel.clearCache(context, false, false, false, true)
                        }
                        viewModel.restoreMissingData(context, restoreAudio, restoreThumbnails, restoreLyrics)
                        showRestoreDialog = false
                    }
                ) { Text(stringResource(R.string.menu_restore_data)) }
            },
            dismissButton = {
                TextButton(onClick = { showRestoreDialog = false }) { Text(stringResource(R.string.dialog_cancel)) }
            }
        )
    }

    if (showExportDialog) {
        AlertDialog(
            onDismissRequest = { showExportDialog = false },
            title = { Text(stringResource(R.string.dialog_export_title)) },
            text = {
                Column {
                    Text(stringResource(R.string.dialog_export_text))
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(
                        onClick = {
                            viewModel.exportData(context, includeFiles = false)
                            showExportDialog = false
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(stringResource(R.string.export_metadata_only))
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(
                        onClick = {
                            viewModel.exportData(context, includeFiles = true)
                            showExportDialog = false
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(stringResource(R.string.export_full_backup))
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showExportDialog = false }) { Text(stringResource(R.string.dialog_cancel)) }
            }
        )
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item {
            Text(
                text = stringResource(R.string.settings_title),
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.padding(bottom = 16.dp)
            )
        }

        item { SettingsHeader(stringResource(R.string.settings_theme)) }
        item {
            SettingsItem(
                title = stringResource(R.string.settings_theme),
                subtitle = when (viewModel.darkMode) {
                    "light" -> stringResource(R.string.theme_light)
                    "dark" -> stringResource(R.string.theme_dark)
                    else -> stringResource(R.string.theme_system)
                },
                icon = Icons.Default.Palette
            ) { showDarkModeDialog = true }
        }

        item { HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp)) }

        item { SettingsHeader(stringResource(R.string.settings_header_storage)) }
        item {
            Card(
                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    if (isRestoring) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(stringResource(R.string.msg_restoring_data), style = MaterialTheme.typography.labelMedium)
                            Spacer(modifier = Modifier.width(16.dp))
                            TextButton(
                                onClick = { 
                                    viewModel.cancelRestoration()
                                    viewModel.removeBatchFromDownloadQueue { it.existingSongId != null }
                                }
                            ) {
                                Text(stringResource(R.string.dialog_cancel))
                            }
                        }
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                            com.example.fluxsona.ui.components.InfoItem(stringResource(R.string.label_total_songs), stats["totalSongs"]?.toString() ?: "0")
                            Spacer(modifier = Modifier.width(16.dp))
                            com.example.fluxsona.ui.components.InfoItem(stringResource(R.string.label_installed), stats["installedSongs"]?.toString() ?: "0")
                        }
                        IconButton(onClick = { 
                            viewModel.refreshSongs(context)
                            viewModel.updateStats(context)
                        }) {
                            Icon(Icons.Default.Refresh, contentDescription = stringResource(R.string.action_recount_songs))
                        }
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        com.example.fluxsona.ui.components.InfoItem(stringResource(R.string.label_audio_storage), com.example.fluxsona.ui.components.formatSize(stats["mp3Size"] as? Long ?: 0L))
                        com.example.fluxsona.ui.components.InfoItem(stringResource(R.string.label_thumbnails), com.example.fluxsona.ui.components.formatSize(stats["thumbSize"] as? Long ?: 0L))
                        com.example.fluxsona.ui.components.InfoItem(stringResource(R.string.dialog_clear_lyrics), com.example.fluxsona.ui.components.formatSize(stats["lyricsSize"] as? Long ?: 0L))
                    }
                }
            }
        }
        item {
            SettingsItem(
                title = stringResource(R.string.settings_clear_cache),
                subtitle = stringResource(R.string.settings_clear_cache_subtitle),
                icon = Icons.Default.DeleteSweep
            ) { showClearCacheDialog = true }
        }
        item {
            SettingsItem(
                title = stringResource(R.string.menu_restore_data),
                subtitle = "Recover missing audio, thumbnails or lyrics",
                icon = Icons.Default.Restore
            ) { showRestoreDialog = true }
        }
        item {
            SettingsItem(
                title = stringResource(R.string.settings_export),
                subtitle = stringResource(R.string.settings_export_subtitle),
                icon = Icons.Default.FileUpload
            ) { showExportDialog = true }
        }
        item {
            SettingsItem(
                title = stringResource(R.string.settings_import),
                subtitle = stringResource(R.string.settings_import_subtitle),
                icon = Icons.Default.FileDownload
            ) { importLauncher.launch(arrayOf("*/*")) }
        }

        item { HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp)) }

        item { SettingsHeader(stringResource(R.string.settings_advanced)) }
        item {
            val cookiesFilePicker = rememberLauncherForActivityResult(
                ActivityResultContracts.OpenDocument()
            ) { uri ->
                uri?.let { viewModel.saveCookiesFile(context, it) }
            }
            
            SettingsItem(
                title = stringResource(R.string.settings_cookies),
                subtitle = viewModel.cookiesFilePath?.let { 
                    stringResource(R.string.settings_cookies_selected, it.substringAfterLast("/")) 
                } ?: stringResource(R.string.settings_cookies_none),
                icon = Icons.Default.Cookie
            ) {
                cookiesFilePicker.launch(arrayOf("text/plain"))
            }
        }
        if (viewModel.cookiesFilePath != null) {
            item {
                TextButton(
                    onClick = { viewModel.clearCookiesFile() },
                    modifier = Modifier.padding(start = 64.dp)
                ) {
                    Text(stringResource(R.string.action_clear_cookies), color = MaterialTheme.colorScheme.error)
                }
            }
        }

        item { HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp)) }

        item { SettingsHeader("Playback Controls") }
        item {
            var showForwardDialog by remember { mutableStateOf(false) }
            if (showForwardDialog) {
                SkipSecondsDialog(
                    title = "Skip Forward Seconds",
                    currentValue = viewModel.skipForwardSeconds,
                    onConfirm = { 
                        viewModel.updateSkipForwardSeconds(it)
                        showForwardDialog = false
                    },
                    onDismiss = { showForwardDialog = false }
                )
            }
            SettingsItem(
                title = "Skip Forward Interval",
                subtitle = "${viewModel.skipForwardSeconds} seconds",
                leadingContent = { 
                    SkipIcon(
                        seconds = viewModel.skipForwardSeconds,
                        isForward = true,
                        contentDescription = ""
                    )
                }
            ) { showForwardDialog = true }
        }
        item {
            var showBackwardDialog by remember { mutableStateOf(false) }
            if (showBackwardDialog) {
                SkipSecondsDialog(
                    title = "Skip Backward Seconds",
                    currentValue = viewModel.skipBackwardSeconds,
                    onConfirm = { 
                        viewModel.updateSkipBackwardSeconds(it)
                        showBackwardDialog = false
                    },
                    onDismiss = { showBackwardDialog = false }
                )
            }
            SettingsItem(
                title = "Skip Backward Interval",
                subtitle = "${viewModel.skipBackwardSeconds} seconds",
                leadingContent = {
                    SkipIcon(
                        seconds = viewModel.skipBackwardSeconds,
                        isForward = false,
                        contentDescription = ""
                    )
                }
            ) { showBackwardDialog = true }
        }

        item { HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp)) }

        item { SettingsHeader("Experimental") }
        item {
            Box(modifier = Modifier.padding(vertical = 8.dp)) {
                Button(
                    onClick = { viewModel.autoTagAllSongs(context) },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !viewModel.isAutoTagging && songs.isNotEmpty(),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    if (viewModel.isAutoTagging) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Auto Tagging...")
                        Spacer(modifier = Modifier.weight(1f))
                        IconButton(onClick = { viewModel.stopAutoTagging() }) {
                            Icon(Icons.Default.Stop, contentDescription = "Stop", tint = Color.White)
                        }
                    } else {
                        Icon(Icons.Default.AutoAwesome, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(stringResource(R.string.action_auto_tag_all))
                    }
                }
            }
        }

        item { HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp)) }

        item { SettingsHeader("App Update") }
        item {
            SettingsItem(
                title = "Check for Updates",
                subtitle = "Current Version: ${context.packageManager.getPackageInfo(context.packageName, 0).versionName}",
                icon = Icons.Default.Update,
                trailingContent = {
                    if (viewModel.isCheckingForUpdates) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                    }
                }
            ) {
                viewModel.checkForUpdates()
            }
        }
    }
}

@Composable
fun SkipSecondsDialog(
    title: String,
    currentValue: Int,
    onConfirm: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    var textValue by remember { mutableStateOf(currentValue.toString()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = textValue,
                onValueChange = { if (it.all { char -> char.isDigit() }) textValue = it },
                label = { Text("Seconds") },
                singleLine = true,
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                    keyboardType = androidx.compose.ui.text.input.KeyboardType.Number
                )
            )
        },
        confirmButton = {
            TextButton(onClick = { 
                val value = textValue.toIntOrNull() ?: 10
                onConfirm(value.coerceIn(1, 60))
            }) { Text("Save") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
fun SettingsHeader(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(vertical = 8.dp)
    )
}

@Composable
fun SettingsItem(
    title: String,
    subtitle: String,
    icon: ImageVector? = null,
    leadingContent: @Composable (() -> Unit)? = null,
    trailingContent: @Composable (() -> Unit)? = null,
    onClick: () -> Unit
) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = { Text(subtitle) },
        leadingContent = leadingContent ?: icon?.let { { Icon(it, contentDescription = null) } },
        trailingContent = trailingContent,
        modifier = Modifier.clickable { onClick() }
    )
}
