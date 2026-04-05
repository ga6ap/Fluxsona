package com.example.fluxsona.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.fluxsona.ui.MusicViewModel

@Composable
fun SettingsScreen(viewModel: MusicViewModel) {
    var showClearCacheDialog by remember { mutableStateOf(false) }

    if (showClearCacheDialog) {
        AlertDialog(
            onDismissRequest = { showClearCacheDialog = false },
            title = { Text("Clear All Cache?") },
            text = { Text("This will remove all downloaded audio, video, thumbnails, and lyrics.") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.clearCache()
                    showClearCacheDialog = false
                }) { Text("Clear") }
            },
            dismissButton = {
                TextButton(onClick = { showClearCacheDialog = false }) { Text("Cancel") }
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
                text = "Settings",
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.padding(bottom = 16.dp)
            )
        }

        item { SettingsHeader("Language & Localization") }
        item {
            SettingsItem(
                title = "App Language",
                subtitle = "English (JSON)",
                icon = Icons.Default.Language
            ) { /* Open language picker */ }
        }
        item {
            SettingsItem(
                title = "Edit Language Files",
                subtitle = "Manually edit lang/*.json",
                icon = Icons.Default.Edit
            ) { /* Open file editor */ }
        }

        item { HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp)) }

        item { SettingsHeader("Storage & Cache") }
        item {
            SettingsItem(
                title = "Clear All Cache",
                subtitle = "Audio, Video, Thumbnails, Lyrics",
                icon = Icons.Default.DeleteSweep
            ) { showClearCacheDialog = true }
        }
        item {
            SettingsItem(
                title = "Export Data",
                subtitle = "Export as .mytunes file",
                icon = Icons.Default.FileUpload
            ) { /* Export */ }
        }
        item {
            SettingsItem(
                title = "Import Data",
                subtitle = "Import from .mytunes file",
                icon = Icons.Default.FileDownload
            ) { /* Import */ }
        }

        item { HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp)) }

        item { SettingsHeader("Playback") }
        item {
            SettingsItem(
                title = "Transitions",
                subtitle = "Crossfade, Silence Buffer",
                icon = Icons.Default.Tune
            ) { /* Configure transitions */ }
        }
    }
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
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit
) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = { Text(subtitle) },
        leadingContent = { Icon(icon, contentDescription = null) },
        modifier = Modifier.clickable { onClick() }
    )
}
