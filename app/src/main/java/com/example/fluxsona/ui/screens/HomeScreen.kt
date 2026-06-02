package com.example.fluxsona.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import kotlinx.coroutines.launch
import androidx.compose.ui.geometry.Offset
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import com.example.fluxsona.data.model.Song
import com.example.fluxsona.data.model.TagState
import com.example.fluxsona.ui.MusicViewModel
import androidx.compose.ui.res.stringResource
import com.example.fluxsona.R
import com.example.fluxsona.ui.components.SongItem
import com.example.fluxsona.ui.components.TagEditorDialog

@Composable
fun HomeScreen(viewModel: MusicViewModel) {
    val context = LocalContext.current
    val filteredSongs by viewModel.filteredSongs.collectAsState()
    val tags by viewModel.tags.collectAsState()
    val tagFilterStates = viewModel.tagFilterStates
    val selectedSongIds = viewModel.selectedSongIds
    val currentSort = viewModel.sortMode
    val searchQuery = viewModel.searchQuery
    
    var songToEditTags by remember { mutableStateOf<Song?>(null) }
    var songMenuTarget by remember { mutableStateOf<Song?>(null) }
    var songForCacheAction by remember { mutableStateOf<Song?>(null) }
    var showDeleteCacheDialog by remember { mutableStateOf(false) }
    
    var songForRestoreAction by remember { mutableStateOf<Song?>(null) }
    var showRestoreDialog by remember { mutableStateOf(false) }
    var restoreAudio by remember { mutableStateOf(true) }
    var restoreThumbnails by remember { mutableStateOf(true) }
    var restoreLyrics by remember { mutableStateOf(true) }
    var clearFailedBeforeRestore by remember { mutableStateOf(false) }
    
    var clearAudio by remember { mutableStateOf(true) }
    var clearThumbnails by remember { mutableStateOf(true) }
    var clearLyrics by remember { mutableStateOf(true) }
    var clearFailed by remember { mutableStateOf(false) }
    
    var showBatchTagDialog by remember { mutableStateOf(false) }
    var isAddingBatchTag by remember { mutableStateOf(true) }
    var showBatchCacheDialog by remember { mutableStateOf(false) }
    var showSortMenu by remember { mutableStateOf(false) }
    
    var showDeleteConfirmDialog by remember { mutableStateOf(false) }
    var isBatchDelete by remember { mutableStateOf(false) }
    var deleteAudioOnDelete by remember { mutableStateOf(true) }
    var deleteThumbnailsOnDelete by remember { mutableStateOf(true) }
    var deleteLyricsOnDelete by remember { mutableStateOf(true) }

    if (showBatchTagDialog) {
        var selectedTagsInDialog by remember { mutableStateOf(setOf<String>()) }
        
        AlertDialog(
            onDismissRequest = { showBatchTagDialog = false },
            title = { Text(if (isAddingBatchTag) stringResource(R.string.dialog_add_tag_selected) else stringResource(R.string.dialog_remove_tag_selected)) },
            text = {
                Column {
                    Text(stringResource(R.string.dialog_select_tag))
                    Spacer(modifier = Modifier.height(8.dp))
                    Box(modifier = Modifier.heightIn(max = 300.dp)) {
                        LazyColumn {
                            items(tags.filter { it.name != "Favourite" }) { tag ->
                                val isTagSelected = selectedTagsInDialog.contains(tag.name)
                                ListItem(
                                    headlineContent = { Text(tag.name) },
                                    leadingContent = {
                                        Checkbox(
                                            checked = isTagSelected,
                                            onCheckedChange = { checked ->
                                                selectedTagsInDialog = if (checked) {
                                                    selectedTagsInDialog + tag.name
                                                } else {
                                                    selectedTagsInDialog - tag.name
                                                }
                                            }
                                        )
                                    },
                                    modifier = Modifier.clickable { 
                                        selectedTagsInDialog = if (isTagSelected) {
                                            selectedTagsInDialog - tag.name
                                        } else {
                                            selectedTagsInDialog + tag.name
                                        }
                                    }
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(
                    enabled = selectedTagsInDialog.isNotEmpty(),
                    onClick = {
                        if (isAddingBatchTag) {
                            viewModel.addTagsToSelected(selectedTagsInDialog.toList())
                        } else {
                            viewModel.removeTagsFromSelected(selectedTagsInDialog.toList())
                        }
                        showBatchTagDialog = false
                    }
                ) {
                    Text(if (isAddingBatchTag) stringResource(R.string.action_add_tag) else stringResource(R.string.action_remove_tag))
                }
            },
            dismissButton = {
                TextButton(onClick = { showBatchTagDialog = false }) { Text(stringResource(R.string.dialog_cancel)) }
            }
        )
    }

    if (showBatchCacheDialog) {
        AlertDialog(
            onDismissRequest = { showBatchCacheDialog = false },
            title = { Text(stringResource(R.string.dialog_clear_cache_multiple, selectedSongIds.size)) },
            text = {
                Column {
                    Text(stringResource(R.string.dialog_clear_cache_text))
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.clickable { clearAudio = !clearAudio }
                    ) {
                        Checkbox(checked = clearAudio, onCheckedChange = { clearAudio = it })
                        Text(stringResource(R.string.dialog_clear_audio))
                    }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.clickable { clearThumbnails = !clearThumbnails }
                    ) {
                        Checkbox(checked = clearThumbnails, onCheckedChange = { clearThumbnails = it })
                        Text(stringResource(R.string.dialog_clear_thumbnails))
                    }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.clickable { clearLyrics = !clearLyrics }
                    ) {
                        Checkbox(checked = clearLyrics, onCheckedChange = { clearLyrics = it })
                        Text(stringResource(R.string.dialog_clear_lyrics))
                    }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
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
                        viewModel.clearCacheForSelectedSongs(clearAudio, clearThumbnails, clearLyrics, clearFailed)
                        showBatchCacheDialog = false
                    }
                ) { Text(stringResource(R.string.dialog_clear)) }
            },
            dismissButton = {
                TextButton(onClick = { showBatchCacheDialog = false }) { Text(stringResource(R.string.dialog_cancel)) }
            }
        )
    }

    if (showDeleteCacheDialog && songForCacheAction != null) {
        AlertDialog(
            onDismissRequest = { 
                showDeleteCacheDialog = false 
                songForCacheAction = null
            },
            title = { Text(stringResource(R.string.dialog_clear_cache_song, songForCacheAction?.title ?: "")) },
            text = {
                Column {
                    Text(stringResource(R.string.dialog_clear_cache_text))
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.clickable { clearAudio = !clearAudio }
                    ) {
                        Checkbox(checked = clearAudio, onCheckedChange = { clearAudio = it })
                        Text(stringResource(R.string.dialog_clear_audio))
                    }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.clickable { clearThumbnails = !clearThumbnails }
                    ) {
                        Checkbox(checked = clearThumbnails, onCheckedChange = { clearThumbnails = it })
                        Text(stringResource(R.string.dialog_clear_thumbnails))
                    }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.clickable { clearLyrics = !clearLyrics }
                    ) {
                        Checkbox(checked = clearLyrics, onCheckedChange = { clearLyrics = it })
                        Text(stringResource(R.string.dialog_clear_lyrics))
                    }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
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
                        songForCacheAction?.let { viewModel.clearSongCache(it, clearAudio, clearThumbnails, clearLyrics, clearFailed) }
                        showDeleteCacheDialog = false
                        songForCacheAction = null
                    }
                ) { Text(stringResource(R.string.dialog_clear)) }
            },
            dismissButton = {
                TextButton(onClick = { 
                    showDeleteCacheDialog = false 
                    songForCacheAction = null
                }) { Text(stringResource(R.string.dialog_cancel)) }
            }
        )
    }

    if (showRestoreDialog) {
        AlertDialog(
            onDismissRequest = { 
                showRestoreDialog = false 
                songForRestoreAction = null
            },
            title = { Text(stringResource(R.string.menu_restore_data)) },
            text = {
                Column {
                    Text(stringResource(R.string.dialog_restore_data_confirm, songForRestoreAction?.title ?: ""))
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.clickable { restoreAudio = !restoreAudio }
                    ) {
                        Checkbox(checked = restoreAudio, onCheckedChange = { restoreAudio = it })
                        Text(stringResource(R.string.dialog_clear_audio))
                    }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.clickable { restoreThumbnails = !restoreThumbnails }
                    ) {
                        Checkbox(checked = restoreThumbnails, onCheckedChange = { restoreThumbnails = it })
                        Text(stringResource(R.string.dialog_clear_thumbnails))
                    }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.clickable { restoreLyrics = !restoreLyrics }
                    ) {
                        Checkbox(checked = restoreLyrics, onCheckedChange = { restoreLyrics = it })
                        Text(stringResource(R.string.dialog_clear_lyrics))
                    }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
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
                            songForRestoreAction?.let { viewModel.clearSongCache(it, false, false, false, true) }
                        }
                        songForRestoreAction?.let { viewModel.redownloadSong(it, context, restoreAudio, restoreThumbnails, restoreLyrics) }
                        showRestoreDialog = false
                        songForRestoreAction = null
                    }
                ) { Text(stringResource(R.string.menu_restore_data)) }
            },
            dismissButton = {
                TextButton(onClick = { 
                    showRestoreDialog = false 
                    songForRestoreAction = null
                }) { Text(stringResource(R.string.dialog_cancel)) }
            }
        )
    }

    if (showDeleteConfirmDialog) {
        AlertDialog(
            onDismissRequest = { 
                showDeleteConfirmDialog = false
                if (!isBatchDelete) songForCacheAction = null
            },
            title = { 
                Text(if (isBatchDelete) stringResource(R.string.dialog_delete_selected_title) 
                     else stringResource(R.string.dialog_delete_confirm_title)) 
            },
            text = {
                Column {
                    Text(if (isBatchDelete) stringResource(R.string.dialog_delete_selected_text, selectedSongIds.size)
                         else stringResource(R.string.dialog_delete_confirm_text))
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    Text(stringResource(R.string.dialog_delete_also_cache), style = MaterialTheme.typography.labelLarge)
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth().clickable { deleteAudioOnDelete = !deleteAudioOnDelete }
                    ) {
                        Checkbox(checked = deleteAudioOnDelete, onCheckedChange = { deleteAudioOnDelete = it })
                        Text(stringResource(R.string.dialog_clear_audio))
                    }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth().clickable { deleteThumbnailsOnDelete = !deleteThumbnailsOnDelete }
                    ) {
                        Checkbox(checked = deleteThumbnailsOnDelete, onCheckedChange = { deleteThumbnailsOnDelete = it })
                        Text(stringResource(R.string.dialog_clear_thumbnails))
                    }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth().clickable { deleteLyricsOnDelete = !deleteLyricsOnDelete }
                    ) {
                        Checkbox(checked = deleteLyricsOnDelete, onCheckedChange = { deleteLyricsOnDelete = it })
                        Text(stringResource(R.string.dialog_clear_lyrics))
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (isBatchDelete) {
                            viewModel.deleteSelectedSongs(context, deleteAudioOnDelete, deleteThumbnailsOnDelete, deleteLyricsOnDelete)
                        } else {
                            songForCacheAction?.let { 
                                viewModel.deleteSong(it, context, deleteAudioOnDelete, deleteThumbnailsOnDelete, deleteLyricsOnDelete) 
                            }
                        }
                        showDeleteConfirmDialog = false
                        songForCacheAction = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text(stringResource(R.string.action_delete))
                }
            },
            dismissButton = {
                TextButton(onClick = { 
                    showDeleteConfirmDialog = false
                    if (!isBatchDelete) songForCacheAction = null
                }) {
                    Text(stringResource(R.string.dialog_cancel))
                }
            }
        )
    }

    if (songToEditTags != null) {
        TagEditorDialog(
            song = songToEditTags!!,
            viewModel = viewModel,
            onDismiss = { songToEditTags = null }
        )
    }

    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()
    var isDragging by remember { mutableStateOf(false) }
    var dragSelectMode by remember { mutableStateOf<Boolean?>(null) }
    val handledIndices = remember { mutableSetOf<Int>() }
    var currentDragPosition by remember { mutableStateOf<Offset?>(null) }
    var autoScrollSpeed by remember { mutableFloatStateOf(0f) }
    var columnBounds by remember { mutableStateOf<androidx.compose.ui.geometry.Rect?>(null) }
    var lazyColumnBounds by remember { mutableStateOf<androidx.compose.ui.geometry.Rect?>(null) }

    LaunchedEffect(isDragging, autoScrollSpeed) {
        if (isDragging && autoScrollSpeed != 0f) {
            while (isDragging && autoScrollSpeed != 0f) {
                listState.scrollBy(autoScrollSpeed)
                currentDragPosition?.let { windowDragPos ->
                    lazyColumnBounds?.let { lazyBounds ->
                        if (windowDragPos.x >= lazyBounds.left && windowDragPos.x <= lazyBounds.right) {
                            val clampedY = windowDragPos.y.coerceIn(lazyBounds.top, lazyBounds.bottom)
                            val relativeOffset = Offset(windowDragPos.x, clampedY) - lazyBounds.topLeft
                            listState.layoutInfo.visibleItemsInfo.find { 
                                relativeOffset.y >= it.offset && relativeOffset.y <= it.offset + it.size
                            }?.let { 
                                val itemIndex = it.index - 2
                                if (itemIndex >= 0 && itemIndex < filteredSongs.size && !handledIndices.contains(itemIndex)) {
                                    viewModel.applySelectionState(filteredSongs[itemIndex].id, dragSelectMode == true)
                                    handledIndices.add(itemIndex)
                                }
                            }
                        }
                    }
                }
                
                kotlinx.coroutines.delay(10)
            }
        }
    }

    val focusManager = androidx.compose.ui.platform.LocalFocusManager.current

    Column(modifier = Modifier
        .fillMaxSize()
        .onGloballyPositioned { columnBounds = it.boundsInWindow() }
        .pointerInput(Unit) {
            detectTapGestures(onTap = {
                focusManager.clearFocus()
            })
        }
        .pointerInput(filteredSongs) {
            detectDragGesturesAfterLongPress(
                onDragStart = { localOffset: Offset ->
                    val windowOffset = columnBounds?.let { localOffset + it.topLeft } ?: localOffset
                    currentDragPosition = windowOffset
                    
                    val itemIndex = lazyColumnBounds?.let { lazyBounds ->
                        if (windowOffset.x >= lazyBounds.left && windowOffset.x <= lazyBounds.right) {
                            val clampedY = windowOffset.y.coerceIn(lazyBounds.top, lazyBounds.bottom)
                            val relativeOffset = Offset(windowOffset.x, clampedY) - lazyBounds.topLeft
                            listState.layoutInfo.visibleItemsInfo.find { 
                                relativeOffset.y >= it.offset && relativeOffset.y <= it.offset + it.size
                            }?.let { it.index - 2 } ?: -1
                        } else -1
                    } ?: -1

                    isDragging = true
                    if (itemIndex >= 0 && itemIndex < filteredSongs.size) {
                        val songId = filteredSongs[itemIndex].id
                        val currentlySelected = selectedSongIds.contains(songId)
                        dragSelectMode = !currentlySelected
                        viewModel.applySelectionState(songId, dragSelectMode == true)
                        handledIndices.add(itemIndex)
                    } else {
                        dragSelectMode = true
                    }
                },
                onDrag = { change: PointerInputChange, _ ->
                    change.consume()
                    val localOffset = change.position
                    val windowOffset = columnBounds?.let { localOffset + it.topLeft } ?: localOffset
                    currentDragPosition = windowOffset
                    
                    lazyColumnBounds?.let { bounds ->
                        val relativeY = windowOffset.y - bounds.top
                        val threshold = bounds.height * 0.30f
                        autoScrollSpeed = when {
                            relativeY < threshold -> {
                                val intensity = ((threshold - relativeY) / threshold).coerceIn(0f, 1f)
                                -45f * (intensity * intensity) - 10f
                            }
                            relativeY > bounds.height - threshold -> {
                                val intensity = ((relativeY - (bounds.height - threshold)) / threshold).coerceIn(0f, 1f)
                                45f * (intensity * intensity) + 10f
                            }
                            else -> 0f
                        }
                    }

                    val itemIndex = lazyColumnBounds?.let { lazyBounds ->
                        if (windowOffset.x >= lazyBounds.left && windowOffset.x <= lazyBounds.right) {
                            val clampedY = windowOffset.y.coerceIn(lazyBounds.top, lazyBounds.bottom)
                            val relativeOffset = Offset(windowOffset.x, clampedY) - lazyBounds.topLeft
                            listState.layoutInfo.visibleItemsInfo.find { 
                                relativeOffset.y >= it.offset && relativeOffset.y <= it.offset + it.size
                            }?.let { it.index - 2 } ?: -1
                        } else -1
                    } ?: -1

                    if (itemIndex >= 0 && itemIndex < filteredSongs.size && !handledIndices.contains(itemIndex)) {
                        val songId = filteredSongs[itemIndex].id
                        viewModel.applySelectionState(songId, dragSelectMode == true)
                        handledIndices.add(itemIndex)
                    }
                },
                onDragEnd = { 
                    isDragging = false
                    dragSelectMode = null
                    handledIndices.clear()
                    autoScrollSpeed = 0f
                    currentDragPosition = null
                    viewModel.clearLastSelectionAction()
                },
                onDragCancel = { 
                    isDragging = false
                    dragSelectMode = null
                    handledIndices.clear()
                    autoScrollSpeed = 0f
                    currentDragPosition = null
                    viewModel.clearLastSelectionAction()
                }
            )
        }
    ) {
        Text(
            text = stringResource(R.string.header_tags),
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(16.dp)
        )
        
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            item {
                FilterChip(
                    selected = viewModel.cacheFilterAudio != TagState.NONE,
                    onClick = { viewModel.cycleAudioFilter() },
                    label = { Text(stringResource(R.string.filter_audio)) },
                    leadingIcon = {
                        when (viewModel.cacheFilterAudio) {
                            TagState.INCLUDED -> Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                            TagState.EXCLUDED -> Icon(Icons.Default.Block, contentDescription = null, modifier = Modifier.size(18.dp))
                            else -> null
                        }
                    },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = when (viewModel.cacheFilterAudio) {
                            TagState.INCLUDED -> MaterialTheme.colorScheme.primaryContainer
                            TagState.EXCLUDED -> MaterialTheme.colorScheme.errorContainer
                            else -> Color.Transparent
                        }
                    )
                )
            }

            item {
                FilterChip(
                    selected = viewModel.cacheFilterThumbnail != TagState.NONE,
                    onClick = { viewModel.cycleThumbnailFilter() },
                    label = { Text(stringResource(R.string.filter_thumbnail)) },
                    leadingIcon = {
                        when (viewModel.cacheFilterThumbnail) {
                            TagState.INCLUDED -> Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                            TagState.EXCLUDED -> Icon(Icons.Default.Block, contentDescription = null, modifier = Modifier.size(18.dp))
                            else -> null
                        }
                    },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = when (viewModel.cacheFilterThumbnail) {
                            TagState.INCLUDED -> MaterialTheme.colorScheme.primaryContainer
                            TagState.EXCLUDED -> MaterialTheme.colorScheme.errorContainer
                            else -> Color.Transparent
                        }
                    )
                )
            }

            item {
                FilterChip(
                    selected = viewModel.cacheFilterLyrics != TagState.NONE,
                    onClick = { viewModel.cycleLyricsFilter() },
                    label = { Text(stringResource(R.string.filter_lyrics)) },
                    leadingIcon = {
                        when (viewModel.cacheFilterLyrics) {
                            TagState.INCLUDED -> Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                            TagState.EXCLUDED -> Icon(Icons.Default.Block, contentDescription = null, modifier = Modifier.size(18.dp))
                            else -> null
                        }
                    },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = when (viewModel.cacheFilterLyrics) {
                            TagState.INCLUDED -> MaterialTheme.colorScheme.primaryContainer
                            TagState.EXCLUDED -> MaterialTheme.colorScheme.errorContainer
                            else -> Color.Transparent
                        }
                    )
                )
            }
            
            item {
                VerticalDivider(modifier = Modifier.height(32.dp).padding(horizontal = 8.dp))
            }

            if (viewModel.isAutoTagging) {
                item {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .background(MaterialTheme.colorScheme.secondaryContainer, RoundedCornerShape(16.dp))
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(stringResource(R.string.msg_auto_tagging), style = MaterialTheme.typography.labelMedium)
                        Spacer(modifier = Modifier.width(8.dp))
                        IconButton(onClick = { viewModel.stopAutoTagging() }, modifier = Modifier.size(24.dp)) {
                            Icon(Icons.Default.Close, contentDescription = "Stop", tint = Color.Red, modifier = Modifier.size(16.dp))
                        }
                    }
                }
            }
            if (viewModel.isRestoring) {
                item {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .background(MaterialTheme.colorScheme.tertiaryContainer, RoundedCornerShape(16.dp))
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(stringResource(R.string.msg_restoring_data), style = MaterialTheme.typography.labelMedium)
                        Spacer(modifier = Modifier.width(8.dp))
                        IconButton(onClick = { 
                            viewModel.cancelRestoration()
                            viewModel.removeBatchFromDownloadQueue { it.existingSongId != null }
                        }, modifier = Modifier.size(24.dp)) {
                            Icon(Icons.Default.Close, contentDescription = "Stop", tint = Color.Red, modifier = Modifier.size(16.dp))
                        }
                    }
                }
            }
            items(tags) { tag ->
                val filterState = tagFilterStates.find { it.name == tag.name }?.state ?: TagState.NONE
                FilterChip(
                    selected = filterState != TagState.NONE,
                    onClick = { viewModel.cycleTagState(tag.name) },
                    label = { Text(tag.name) },
                    leadingIcon = {
                        when (filterState) {
                            TagState.INCLUDED -> Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                            TagState.EXCLUDED -> Icon(Icons.Default.Block, contentDescription = null, modifier = Modifier.size(18.dp))
                            TagState.DISJUNCTION -> Icon(Icons.Default.DoneAll, contentDescription = null, modifier = Modifier.size(18.dp))
                            else -> null
                        }
                    },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = when (filterState) {
                            TagState.INCLUDED -> MaterialTheme.colorScheme.primaryContainer
                            TagState.EXCLUDED -> MaterialTheme.colorScheme.errorContainer
                            TagState.DISJUNCTION -> Color.Magenta.copy(alpha = 0.4f)
                            else -> Color.Transparent
                        }
                    )
                )
            }
        }

        if (selectedSongIds.isNotEmpty()) {
            Surface(
                tonalElevation = 8.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(vertical = 4.dp, horizontal = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = selectedSongIds.size.toString(),
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(horizontal = 8.dp)
                    )

                    VerticalDivider(modifier = Modifier.height(24.dp).padding(horizontal = 4.dp))

                    val isAllSelected = filteredSongs.isNotEmpty() && selectedSongIds.size >= filteredSongs.size
                    val actions = listOf(
                        Triple(if (isAllSelected) Icons.Default.Deselect else Icons.Default.SelectAll, 
                               if (isAllSelected) R.string.action_clear_selection else R.string.action_select_all) { 
                            if (isAllSelected) viewModel.clearSelection() else viewModel.selectAllFiltered() 
                        },
                        Triple(Icons.Default.Add, R.string.action_add_tag) { 
                            isAddingBatchTag = true
                            showBatchTagDialog = true 
                        },
                        Triple(Icons.Default.Remove, R.string.action_remove_tag) { 
                            isAddingBatchTag = false
                            showBatchTagDialog = true 
                        },
                        Triple(Icons.Default.Download, R.string.menu_restore_data) {
                            viewModel.restoreMissingData(context, restoreAudio = true, restoreThumbnails = true, restoreLyrics = true, specificSongIds = selectedSongIds.toList())
                        },
                        Triple(Icons.Default.SwapVert, R.string.action_reverse_order) {
                            viewModel.reverseSelectedOrder()
                        },
                        Triple(Icons.Default.DeleteSweep, R.string.settings_clear_cache) { showBatchCacheDialog = true },
                        Triple(Icons.Default.Delete, R.string.action_delete_all) { 
                            isBatchDelete = true
                            showDeleteConfirmDialog = true
                        },
                    )

                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        actions.forEach { (icon, descRes, onClick) ->
                            IconButton(onClick = onClick, modifier = Modifier.size(40.dp)) {
                                Icon(
                                    imageVector = icon, 
                                    contentDescription = stringResource(descRes),
                                    tint = if (icon == Icons.Default.Delete) Color.Red else LocalContentColor.current,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                        }
                    }
                    
                    VerticalDivider(modifier = Modifier.height(24.dp).padding(horizontal = 4.dp))
                    
                    IconButton(onClick = { viewModel.clearSelection() }, modifier = Modifier.size(40.dp)) {
                        Icon(Icons.Default.Close, contentDescription = stringResource(R.string.action_clear_selection), modifier = Modifier.size(22.dp))
                    }
                }
            }
        }

        Box(modifier = Modifier.weight(1f)) {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .onGloballyPositioned { lazyColumnBounds = it.boundsInWindow() },
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = stringResource(R.string.header_songs),
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (currentSort == "SHUFFLE") {
                                IconButton(onClick = { viewModel.reshuffle() }) {
                                    Icon(Icons.Default.Refresh, contentDescription = stringResource(R.string.action_reshuffle))
                                }
                            }
                            IconButton(onClick = { showSortMenu = true }) {
                                Icon(Icons.Default.Sort, contentDescription = stringResource(R.string.menu_sort))
                                DropdownMenu(expanded = showSortMenu, onDismissRequest = { showSortMenu = false }) {
                                    if (currentSort == "SYNCED_QUEUE") {
                                        DropdownMenuItem(
                                            text = { Text("Active Queue (Temporary)") },
                                            onClick = { showSortMenu = false },
                                            trailingIcon = { Icon(Icons.Default.Check, contentDescription = null) }
                                        )
                                        HorizontalDivider()
                                    }
                                    val sortOptions = listOf(
                                        "DATE_ADDED_DESC" to R.string.sort_date_added_desc,
                                        "DATE_ADDED_ASC" to R.string.sort_date_added_asc,
                                        "DURATION_ASC" to R.string.sort_duration_asc,
                                        "DURATION_DESC" to R.string.sort_duration_desc,
                                        "TITLE_ASC" to R.string.sort_title_asc,
                                        "TITLE_DESC" to R.string.sort_title_desc,
                                        "ARTIST_ASC" to R.string.sort_artist_asc,
                                        "ARTIST_DESC" to R.string.sort_artist_desc,
                                        "SHUFFLE" to R.string.sort_shuffle
                                    )
                                    sortOptions.forEach { (mode, labelRes) ->
                                        DropdownMenuItem(
                                            text = { Text(stringResource(labelRes)) },
                                            onClick = {
                                                viewModel.updateSortMode(mode)
                                                showSortMenu = false
                                            },
                                            trailingIcon = { if (currentSort == mode) Icon(Icons.Default.Check, contentDescription = null) }
                                        )
                                    }
                                }
                            }
                            
                            val isAllSelected = filteredSongs.isNotEmpty() && selectedSongIds.size >= filteredSongs.size
                            TextButton(onClick = { if (isAllSelected) viewModel.clearSelection() else viewModel.selectAllFiltered() }) {
                                Text(
                                    text = if (isAllSelected) stringResource(R.string.action_clear_selection) else stringResource(R.string.action_select_all),
                                    style = MaterialTheme.typography.labelLarge
                                )
                            }
                        }
                    }
                }
                
                item {
                    Column {
                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = { viewModel.updateSearchQuery(it) },
                            modifier = Modifier.fillMaxWidth(),
                            placeholder = { Text(stringResource(R.string.hint_search_songs)) },
                            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                            trailingIcon = {
                                if (searchQuery.isNotEmpty()) {
                                    IconButton(onClick = { viewModel.updateSearchQuery("") }) {
                                        Icon(Icons.Default.Close, contentDescription = null)
                                    }
                                }
                            },
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp)
                        )
                    }
                }
                
                if (filteredSongs.isEmpty()) {
                    item {
                        Text(stringResource(R.string.msg_no_songs_filters), style = MaterialTheme.typography.bodyMedium)
                    }
                } else {
                    itemsIndexed(filteredSongs, key = { _, song -> song.id }) { index, song ->
                        val isSelected = selectedSongIds.contains(song.id)
                        Box(modifier = Modifier.fillMaxWidth()) {
                            SongItem(
                                song = song,
                                isFavorite = song.isFavorite,
                                onFavoriteClick = { viewModel.toggleFavorite(song.id) },
                                onClick = { 
                                    if (selectedSongIds.isNotEmpty() || isDragging) {
                                        viewModel.toggleSelection(song.id)
                                    } else {
                                        viewModel.playSong(context, song)
                                    }
                                },
                                onLongClick = null,
                                onMoreClick = { songMenuTarget = song },
                                showCheckbox = selectedSongIds.isNotEmpty(),
                                isSelected = isSelected,
                                onSelectionChange = { viewModel.toggleSelection(song.id) },
                                modifier = Modifier.background(
                                    if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                                    else Color.Transparent
                                ),
                                trailingContent = {
                                    if (songMenuTarget?.id == song.id) {
                                        DropdownMenu(
                                            expanded = true,
                                            onDismissRequest = { songMenuTarget = null },
                                            offset = androidx.compose.ui.unit.DpOffset(x = 0.dp, y = 0.dp)
                                        ) {
                                            DropdownMenuItem(
                                                text = { Text(stringResource(R.string.menu_edit_tags)) },
                                                onClick = {
                                                    songToEditTags = songMenuTarget
                                                    songMenuTarget = null
                                                },
                                                leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null) }
                                            )
                                            DropdownMenuItem(
                                                text = { Text(stringResource(R.string.menu_select)) },
                                                onClick = {
                                                    viewModel.toggleSelection(song.id)
                                                    songMenuTarget = null
                                                },
                                                leadingIcon = { Icon(Icons.Default.LibraryAddCheck, contentDescription = null) }
                                            )
                                            DropdownMenuItem(
                                                text = { Text(stringResource(R.string.settings_clear_cache)) },
                                                onClick = {
                                                    songForCacheAction = songMenuTarget
                                                    showDeleteCacheDialog = true
                                                    songMenuTarget = null
                                                },
                                                leadingIcon = { Icon(Icons.Default.DeleteSweep, contentDescription = null) }
                                            )
                                            DropdownMenuItem(
                                                text = { Text(stringResource(R.string.menu_restore_data)) },
                                                onClick = {
                                                    songForRestoreAction = songMenuTarget
                                                    showRestoreDialog = true
                                                    songMenuTarget = null
                                                },
                                                leadingIcon = { Icon(Icons.Default.Download, contentDescription = null) }
                                            )
                                            DropdownMenuItem(
                                                text = { Text(stringResource(R.string.action_auto_tag)) },
                                                onClick = {
                                                    viewModel.autoTagSong(song)
                                                    songMenuTarget = null
                                                },
                                                leadingIcon = { Icon(Icons.Default.AutoAwesome, contentDescription = null) }
                                            )
                                            DropdownMenuItem(
                                                text = { Text(stringResource(R.string.menu_delete_entirely), color = MaterialTheme.colorScheme.error) },
                                                onClick = {
                                                    songForCacheAction = songMenuTarget
                                                    isBatchDelete = false
                                                    showDeleteConfirmDialog = true
                                                    songMenuTarget = null
                                                },
                                                leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error) }
                                            )
                                        }
                                    }
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}
