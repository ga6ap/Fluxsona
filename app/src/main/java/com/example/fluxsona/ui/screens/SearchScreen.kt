package com.example.fluxsona.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.TravelExplore
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.example.fluxsona.R
import com.example.fluxsona.data.model.Song
import com.example.fluxsona.ui.MusicViewModel
import com.example.fluxsona.ui.components.SongItem

enum class SearchSource(val labelRes: Int, val icon: String) {
    YOUTUBE(R.string.search_source_youtube, "YT"),
    YOUTUBE_MUSIC(R.string.search_source_youtube_music, "YTM"),
    APPLE_MUSIC(R.string.search_source_apple_music, "AM"),
    SOUNDCLOUD(R.string.search_source_soundcloud, "SC")
}


enum class SearchMode { SCRAPE, LINK }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(viewModel: MusicViewModel) {
    val context = LocalContext.current
    var searchQuery by remember { mutableStateOf("") }
    var selectedSource by remember { mutableStateOf(SearchSource.YOUTUBE) }
    var searchMode by remember { mutableStateOf(SearchMode.LINK) }
    val searchResults = remember { mutableStateListOf<Song>() }

    val downloadQueue = viewModel.downloadQueue
    val totalDownloadProgress = viewModel.totalDownloadProgress
    var showQueueSheet by remember { mutableStateOf(false) }

    var showDownloadOptionsDialog by remember { mutableStateOf<String?>(null) }
    var downloadAudio by remember { mutableStateOf(true) }
    var downloadThumbnail by remember { mutableStateOf(true) }
    var downloadLyrics by remember { mutableStateOf(true) }

    if (showDownloadOptionsDialog != null) {
        AlertDialog(
            onDismissRequest = { showDownloadOptionsDialog = null },
            title = { Text(stringResource(R.string.dialog_download_options_title)) },
            text = {
                Column {
                    Text(stringResource(R.string.dialog_download_options_text))
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.clickable { downloadAudio = !downloadAudio }
                    ) {
                        Checkbox(checked = downloadAudio, onCheckedChange = { downloadAudio = it })
                        Text(stringResource(R.string.dialog_clear_audio))
                    }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.clickable { downloadThumbnail = !downloadThumbnail }
                    ) {
                        Checkbox(checked = downloadThumbnail, onCheckedChange = { downloadThumbnail = it })
                        Text(stringResource(R.string.dialog_clear_thumbnails))
                    }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.clickable { downloadLyrics = !downloadLyrics }
                    ) {
                        Checkbox(checked = downloadLyrics, onCheckedChange = { downloadLyrics = it })
                        Text(stringResource(R.string.dialog_clear_lyrics))
                    }
                }
            },
            confirmButton = {
                TextButton(
                    enabled = downloadAudio || downloadThumbnail || downloadLyrics,
                    onClick = {
                        showDownloadOptionsDialog?.let { url ->
                            viewModel.addToDownloadQueue(url, context, downloadAudio, downloadThumbnail, downloadLyrics)
                        }
                        showDownloadOptionsDialog = null
                    }
                ) { Text(stringResource(R.string.action_download)) }
            },
            dismissButton = {
                TextButton(onClick = { showDownloadOptionsDialog = null }) { Text(stringResource(R.string.dialog_cancel)) }
            }
        )
    }

    val focusManager = androidx.compose.ui.platform.LocalFocusManager.current

    Column(modifier = Modifier
        .fillMaxSize()
        .pointerInput(Unit) {
            detectTapGestures(onTap = {
                focusManager.clearFocus()
            })
        }
    ) {
        if (downloadQueue.isNotEmpty()) {
            Surface(
                onClick = { showQueueSheet = true },
                tonalElevation = 4.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(8.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        val currentTask = downloadQueue.find { it.isDownloading }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = stringResource(R.string.header_download_queue) + " (${downloadQueue.size})",
                                style = MaterialTheme.typography.labelLarge
                            )
                            if (currentTask != null) {
                                Text(
                                    text = currentTask.currentOperation,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                                )
                            }
                        }
                        Text(
                            text = "${(totalDownloadProgress * 100).toInt()}%",
                            style = MaterialTheme.typography.labelMedium
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    LinearProgressIndicator(
                        progress = { totalDownloadProgress },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }

        if (showQueueSheet) {
            ModalBottomSheet(
                onDismissRequest = { showQueueSheet = false }
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        stringResource(R.string.header_download_queue),
                        style = MaterialTheme.typography.titleLarge,
                        modifier = Modifier.padding(bottom = 16.dp)
                    )
                    
                    if (downloadQueue.isEmpty()) {
                        Text(stringResource(R.string.msg_queue_empty), style = MaterialTheme.typography.bodyMedium)
                    }

                    LazyColumn(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(downloadQueue, key = { it.id }) { task ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        task.title,
                                        style = MaterialTheme.typography.bodyMedium,
                                        maxLines = 1,
                                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                                    )
                                    Text(
                                        task.currentOperation,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    if (task.error != null) {
                                        Text(task.error, color = Color.Red, style = MaterialTheme.typography.labelSmall)
                                    } else {
                                        LinearProgressIndicator(
                                            progress = { task.progress },
                                            modifier = Modifier.fillMaxWidth()
                                        )
                                    }
                                }
                                IconButton(onClick = { viewModel.removeFromDownloadQueue(task.id) }) {
                                    Icon(Icons.Default.Close, contentDescription = stringResource(R.string.action_remove))
                                }
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(32.dp))
                }
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            SearchBar(
                query = searchQuery,
                onQueryChange = { searchQuery = it },
                onSearch = { 
                    if (searchMode == SearchMode.SCRAPE) {
                        searchResults.clear()
                        val sourceLabel = context.getString(selectedSource.labelRes)
                        val searchPrefix = when(selectedSource) {
                            SearchSource.YOUTUBE -> "ytsearch"
                            SearchSource.YOUTUBE_MUSIC -> "ytsearch"
                            SearchSource.SOUNDCLOUD -> "scsearch"
                            else -> "ytsearch"
                        }
                        searchResults.add(Song(
                            id = "${selectedSource.name.lowercase()}_${System.currentTimeMillis()}",
                            title = context.getString(R.string.search_scraped_title, sourceLabel, it),
                            artist = context.getString(R.string.search_scraped_artist, sourceLabel),
                            duration = "3:30",
                            thumbnailUri = null,
                            originalUrl = "$searchPrefix:$it"
                        ))
                    } else {
                        showDownloadOptionsDialog = searchQuery
                        searchQuery = ""
                    }
                },
                active = false,
                onActiveChange = {},
                placeholder = { 
                    Text(
                        if (searchMode == SearchMode.SCRAPE) 
                            stringResource(R.string.search_placeholder_scrape, stringResource(selectedSource.labelRes)) 
                        else 
                            stringResource(R.string.search_placeholder_link)
                    )
                },
                leadingIcon = { 
                    Icon(
                        if (searchMode == SearchMode.SCRAPE) Icons.Default.Search else Icons.Default.Link, 
                        contentDescription = null
                    ) 
                },
                modifier = Modifier.weight(1f)
            ) { }

            Spacer(modifier = Modifier.width(8.dp))

            FilledTonalIconButton(
                onClick = { 
                    searchMode = if (searchMode == SearchMode.SCRAPE) SearchMode.LINK else SearchMode.SCRAPE 
                }
            ) {
                Icon(
                    imageVector = if (searchMode == SearchMode.SCRAPE) Icons.Default.TravelExplore else Icons.Default.Link,
                    contentDescription = stringResource(R.string.acc_toggle_search_mode)
                )
            }
        }

        if (searchMode == SearchMode.SCRAPE) {
            LazyRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(SearchSource.values()) { source ->
                    FilterChip(
                        selected = selectedSource == source,
                        onClick = { selectedSource = source },
                        label = { Text(stringResource(source.labelRes)) },
                        leadingIcon = if (selectedSource == source) {
                            { Text(source.icon, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold) }
                        } else null
                    )
                }
            }
        } else {
            Text(
                stringResource(R.string.search_link_mode_hint),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )
        }

        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(searchResults, key = { it.id }) { song ->
                SongItem(
                    song = song,
                    onClick = { 
                        song.originalUrl?.let { url ->
                            showDownloadOptionsDialog = url
                        }
                    }
                )
            }
        }
    }
}
