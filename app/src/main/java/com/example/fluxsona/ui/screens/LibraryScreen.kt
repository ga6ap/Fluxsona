package com.example.fluxsona.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.horizontalScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.foundation.rememberScrollState
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import androidx.compose.animation.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.material3.InputChip
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.example.fluxsona.R
import com.example.fluxsona.data.model.Tag
import com.example.fluxsona.data.model.TagState
import com.example.fluxsona.ui.MusicViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(viewModel: MusicViewModel) {
    val context = LocalContext.current
    
    var showRestoreDialog by remember { mutableStateOf(false) }
    var showBatchDeleteConfirm by remember { mutableStateOf(false) }
    var restoreAudio by remember { mutableStateOf(true) }
    var restoreThumbnails by remember { mutableStateOf(true) }
    var restoreLyrics by remember { mutableStateOf(true) }
    var clearFailedBeforeRestore by remember { mutableStateOf(false) }
    
    var showClearCacheDialog by remember { mutableStateOf(false) }
    var clearAudio by remember { mutableStateOf(true) }
    var clearThumbnails by remember { mutableStateOf(true) }
    var clearLyrics by remember { mutableStateOf(true) }
    var clearFailed by remember { mutableStateOf(false) }
    
    val songs by viewModel.songs.collectAsState()

    if (showRestoreDialog) {
        AlertDialog(
            onDismissRequest = { showRestoreDialog = false },
            title = { Text(stringResource(R.string.menu_restore_data)) },
            text = {
                Column {
                    Text(stringResource(R.string.dialog_restore_data_multiple))
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

    if (showBatchDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showBatchDeleteConfirm = false },
            title = { Text(stringResource(R.string.dialog_delete_tag_title)) },
            text = { Text("Are you sure you want to delete ${viewModel.selectedTagNames.size} tags?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteSelectedTags()
                        showBatchDeleteConfirm = false
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) { Text(stringResource(R.string.action_delete)) }
            },
            dismissButton = {
                TextButton(onClick = { showBatchDeleteConfirm = false }) { Text(stringResource(R.string.dialog_cancel)) }
            }
        )
    }
    
    val focusManager = androidx.compose.ui.platform.LocalFocusManager.current
    val tags by viewModel.tags.collectAsState()
    val listState = rememberLazyListState()
    
    var isDragging by remember { mutableStateOf(false) }
    var dragSelectMode by remember { mutableStateOf<Boolean?>(null) }
    val handledKeys = remember { mutableSetOf<String>() }
    var currentDragPosition by remember { mutableStateOf<androidx.compose.ui.geometry.Offset?>(null) }
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
                            val relativeOffset = androidx.compose.ui.geometry.Offset(windowDragPos.x, clampedY) - lazyBounds.topLeft
                            listState.layoutInfo.visibleItemsInfo.find { 
                                relativeOffset.y >= it.offset && relativeOffset.y <= it.offset + it.size
                            }?.let { visibleItem ->
                                val key = visibleItem.key as? String
                                if (key != null && key != "Favourite" && !handledKeys.contains(key)) {
                                    val shouldSelect = dragSelectMode == true
                                    if (shouldSelect) {
                                        if (!viewModel.selectedTagNames.contains(key)) viewModel.selectedTagNames.add(key)
                                    } else {
                                        viewModel.selectedTagNames.remove(key)
                                    }
                                    handledKeys.add(key)
                                }
                            }
                        }
                    }
                }
                kotlinx.coroutines.delay(10)
            }
        }
    }

    Column(modifier = Modifier
        .fillMaxSize()
        .onGloballyPositioned { columnBounds = it.boundsInWindow() }
        .pointerInput(tags) {
            coroutineScope {
                launch {
                    detectTapGestures(onTap = {
                        focusManager.clearFocus()
                    })
                }
                launch {
                    detectDragGesturesAfterLongPress(
                        onDragStart = { localOffset ->
                            val windowOffset = columnBounds?.let { localOffset + it.topLeft } ?: localOffset
                            currentDragPosition = windowOffset
                            
                            val itemKey = lazyColumnBounds?.let { lazyBounds ->
                                if (windowOffset.x >= lazyBounds.left && windowOffset.x <= lazyBounds.right) {
                                    val clampedY = windowOffset.y.coerceIn(lazyBounds.top, lazyBounds.bottom)
                                    val relativeOffset = androidx.compose.ui.geometry.Offset(windowOffset.x, clampedY) - lazyBounds.topLeft
                                    listState.layoutInfo.visibleItemsInfo.find { 
                                        relativeOffset.y >= it.offset && relativeOffset.y <= it.offset + it.size
                                    }?.key as? String
                                } else null
                            }

                            isDragging = true
                            handledKeys.clear()
                            if (itemKey != null) {
                                if (itemKey != "Favourite") {
                                    val currentlySelected = viewModel.selectedTagNames.contains(itemKey)
                                    dragSelectMode = !currentlySelected
                                    if (dragSelectMode == true) {
                                        if (!viewModel.selectedTagNames.contains(itemKey)) viewModel.selectedTagNames.add(itemKey)
                                    } else {
                                        viewModel.selectedTagNames.remove(itemKey)
                                    }
                                    handledKeys.add(itemKey)
                                } else {
                                    dragSelectMode = true
                                }
                            } else {
                                dragSelectMode = true
                            }
                        },
                        onDrag = { change, _ ->
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

                            val itemKey = lazyColumnBounds?.let { lazyBounds ->
                                if (windowOffset.x >= lazyBounds.left && windowOffset.x <= lazyBounds.right) {
                                    val clampedY = windowOffset.y.coerceIn(lazyBounds.top, lazyBounds.bottom)
                                    val relativeOffset = androidx.compose.ui.geometry.Offset(windowOffset.x, clampedY) - lazyBounds.topLeft
                                    listState.layoutInfo.visibleItemsInfo.find { 
                                        relativeOffset.y >= it.offset && relativeOffset.y <= it.offset + it.size
                                    }?.key as? String
                                } else null
                            }

                            if (itemKey != null) {
                                if (itemKey != "Favourite" && !handledKeys.contains(itemKey)) {
                                    val shouldSelect = dragSelectMode == true
                                    if (shouldSelect) {
                                        if (!viewModel.selectedTagNames.contains(itemKey)) viewModel.selectedTagNames.add(itemKey)
                                    } else {
                                        viewModel.selectedTagNames.remove(itemKey)
                                    }
                                    handledKeys.add(itemKey)
                                }
                            }
                        },
                        onDragEnd = { 
                            isDragging = false
                            dragSelectMode = null
                            handledKeys.clear()
                            autoScrollSpeed = 0f
                            currentDragPosition = null
                        },
                        onDragCancel = { 
                            isDragging = false
                            dragSelectMode = null
                            handledKeys.clear()
                            autoScrollSpeed = 0f
                            currentDragPosition = null
                        }
                    )
                }
            }
        }
    ) {
        if (viewModel.selectedTagNames.isNotEmpty()) {
            Surface(
                tonalElevation = 8.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(vertical = 4.dp, horizontal = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = viewModel.selectedTagNames.size.toString(),
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(horizontal = 8.dp)
                    )

                    VerticalDivider(modifier = Modifier.height(24.dp).padding(horizontal = 4.dp))

                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val isAllSelected = tags.filter { it.name != "Favourite" }.let { filtered ->
                            filtered.isNotEmpty() && viewModel.selectedTagNames.size >= filtered.size
                        }
                        IconButton(onClick = { if (isAllSelected) viewModel.clearTagSelection() else viewModel.selectAllTags() }) {
                            Icon(
                                if (isAllSelected) Icons.Default.Deselect else Icons.Default.SelectAll, 
                                contentDescription = if (isAllSelected) stringResource(R.string.action_clear_selection) else stringResource(R.string.action_select_all)
                            )
                        }
                        IconButton(onClick = { viewModel.setBatchTagState(TagState.NONE) }) {
                            Icon(Icons.Default.Tag, contentDescription = "Set Default")
                        }
                        IconButton(onClick = { viewModel.setBatchTagState(TagState.INCLUDED) }) {
                            Icon(Icons.Default.Check, contentDescription = "Set Include", tint = MaterialTheme.colorScheme.primary)
                        }
                        IconButton(onClick = { viewModel.setBatchTagState(TagState.EXCLUDED) }) {
                            Icon(Icons.Default.Block, contentDescription = "Set Exclude", tint = MaterialTheme.colorScheme.error)
                        }
                        IconButton(onClick = { viewModel.setBatchTagState(TagState.DISJUNCTION) }) {
                            Icon(Icons.Default.DoneAll, contentDescription = "Set Disjunction", tint = Color.Magenta)
                        }
                        IconButton(onClick = { viewModel.cycleBatchTagState() }) {
                            Icon(Icons.Default.Refresh, contentDescription = "Cycle States")
                        }
                        var showCategoryAssignDialog by remember { mutableStateOf(false) }
                        if (showCategoryAssignDialog) {
                            var targetCategory by remember { mutableStateOf("Default") }
                            var dropdownExpanded by remember { mutableStateOf(false) }
                            val categories = remember(tags) { 
                                tags.map { it.category }.distinct().sortedWith(compareByDescending<String> { it == "Default" }.thenBy { it })
                            }
                            
                            AlertDialog(
                                onDismissRequest = { showCategoryAssignDialog = false },
                                title = { Text("Assign Category") },
                                text = {
                                    Column {
                                        Text("Select category for ${viewModel.selectedTagNames.size} tags")
                                        Spacer(modifier = Modifier.height(16.dp))
                                        Box {
                                            OutlinedButton(
                                                onClick = { dropdownExpanded = true },
                                                modifier = Modifier.fillMaxWidth()
                                            ) {
                                                Text(targetCategory)
                                                Spacer(modifier = Modifier.weight(1f))
                                                Icon(Icons.Default.ArrowDropDown, contentDescription = null)
                                            }
                                            DropdownMenu(
                                                expanded = dropdownExpanded,
                                                onDismissRequest = { dropdownExpanded = false }
                                            ) {
                                                categories.forEach { cat ->
                                                    DropdownMenuItem(
                                                        text = { Text(cat) },
                                                        onClick = {
                                                            targetCategory = cat
                                                            dropdownExpanded = false
                                                        }
                                                    )
                                                }
                                            }
                                        }
                                    }
                                },
                                confirmButton = {
                                    TextButton(onClick = {
                                        viewModel.assignTagsToCategory(viewModel.selectedTagNames.toList(), targetCategory)
                                        showCategoryAssignDialog = false
                                        viewModel.clearTagSelection()
                                    }) { Text("Confirm") }
                                },
                                dismissButton = {
                                    TextButton(onClick = { showCategoryAssignDialog = false }) { Text(stringResource(R.string.dialog_cancel)) }
                                }
                            )
                        }
                        
                        IconButton(onClick = { showCategoryAssignDialog = true }) {
                            Icon(Icons.Default.Category, contentDescription = "Move to Category")
                        }

                        IconButton(onClick = { showBatchDeleteConfirm = true }) {
                            Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.action_delete), tint = Color.Red)
                        }
                    }

                    VerticalDivider(modifier = Modifier.height(24.dp).padding(horizontal = 4.dp))

                    IconButton(onClick = { viewModel.clearTagSelection() }) {
                        Icon(Icons.Default.Close, contentDescription = stringResource(R.string.action_clear_selection))
                    }
                }
            }
        }


        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = stringResource(R.string.header_tags),
                style = MaterialTheme.typography.headlineMedium
            )
            
            val isAllSelected = tags.filter { it.name != "Favourite" }.let { filtered ->
                filtered.isNotEmpty() && viewModel.selectedTagNames.size >= filtered.size
            }
            TextButton(onClick = { if (isAllSelected) viewModel.clearTagSelection() else viewModel.selectAllTags() }) {
                Text(
                    text = if (isAllSelected) stringResource(R.string.action_clear_selection) else stringResource(R.string.action_select_all),
                    style = MaterialTheme.typography.labelLarge
                )
            }
        }
        TagList(
            viewModel = viewModel, 
            listState = listState,
            onGloballyPositioned = { lazyColumnBounds = it.boundsInWindow() },
            modifier = Modifier.weight(1f)
        )
        
        var customAuthor by remember { mutableStateOf("") }
        var customTitle by remember { mutableStateOf("") }
        var showCustomFilters by remember { mutableStateOf(false) }

        TextButton(
            onClick = { showCustomFilters = !showCustomFilters },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp)
        ) {
            Icon(if (showCustomFilters) Icons.Default.ExpandLess else Icons.Default.ExpandMore, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text("Custom Keyword Filters")
        }

        androidx.compose.animation.AnimatedVisibility(visible = showCustomFilters) {
            Surface(
                tonalElevation = 2.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    // Author Filters
                    OutlinedTextField(
                        value = customAuthor,
                        onValueChange = { customAuthor = it },
                        label = { Text("Add Author Keyword") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        trailingIcon = {
                            IconButton(onClick = { 
                                if (customAuthor.isNotBlank()) {
                                    viewModel.customAuthorFilters.add(customAuthor)
                                    customAuthor = ""
                                }
                            }) { Icon(Icons.Default.Add, contentDescription = null) }
                        }
                    )
                    
                    FlowRow(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        viewModel.customAuthorFilters.forEach { filter ->
                            InputChip(
                                selected = true,
                                onClick = { viewModel.customAuthorFilters.remove(filter) },
                                label = { Text(filter) },
                                trailingIcon = { Icon(Icons.Default.Close, modifier = Modifier.size(16.dp), contentDescription = null) }
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(4.dp))
                    
                    // Title Filters
                    OutlinedTextField(
                        value = customTitle,
                        onValueChange = { customTitle = it },
                        label = { Text("Add Title Keyword") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        trailingIcon = {
                            IconButton(onClick = { 
                                if (customTitle.isNotBlank()) {
                                    viewModel.customTitleFilters.add(customTitle)
                                    customTitle = ""
                                }
                            }) { Icon(Icons.Default.Add, contentDescription = null) }
                        }
                    )

                    FlowRow(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        viewModel.customTitleFilters.forEach { filter ->
                            InputChip(
                                selected = true,
                                onClick = { viewModel.customTitleFilters.remove(filter) },
                                label = { Text(filter) },
                                trailingIcon = { Icon(Icons.Default.Close, modifier = Modifier.size(16.dp), contentDescription = null) }
                            )
                        }
                    }
                }
            }
        }

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
}

@Composable
fun InfoItem(label: String, value: String) {
    Column {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
        Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
    }
}

fun formatSize(bytes: Long): String {
    if (bytes <= 0) return "0 B"
    val units = arrayOf("B", "KB", "MB", "GB", "TB")
    val digitGroups = (Math.log10(bytes.toDouble()) / Math.log10(1024.0)).toInt()
    return String.format(java.util.Locale.UK, "%.1f %s", bytes / Math.pow(1024.0, digitGroups.toDouble()), units[digitGroups])
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun TagList(
    viewModel: MusicViewModel, 
    listState: androidx.compose.foundation.lazy.LazyListState,
    onGloballyPositioned: (androidx.compose.ui.layout.LayoutCoordinates) -> Unit,
    modifier: Modifier = Modifier
) {
    val tags by viewModel.tags.collectAsState()
    val songs by viewModel.songs.collectAsState()
    val selectedTagNames = viewModel.selectedTagNames
    var tagToEdit by remember { mutableStateOf<Tag?>(null) }
    var showDeleteDialog by remember { mutableStateOf<Tag?>(null) }

    val groupedTags = tags.groupBy { it.category }
    val sortedCategories = groupedTags.keys.sortedWith(compareByDescending<String> { it == "Default" }.thenBy { it })
    
    val expandedCategories = remember { mutableStateMapOf<String, Boolean>() }
    // Initialize all as expanded by default
    LaunchedEffect(sortedCategories) {
        sortedCategories.forEach { if (!expandedCategories.containsKey(it)) expandedCategories[it] = true }
    }

    LazyColumn(
        state = listState,
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = modifier.onGloballyPositioned { onGloballyPositioned(it) }
    ) {
        sortedCategories.forEach { category ->
            item {
                val isExpanded = expandedCategories[category] ?: true
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp) // Standardize height for all headers
                        .clickable { expandedCategories[category] = !isExpanded }
                        .padding(vertical = 4.dp), // Consistent padding
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = category,
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.weight(1f)
                    )
                    Icon(
                        if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    if (category != "Default") {
                        IconButton(onClick = { viewModel.deleteCategory(category) }) {
                            Icon(Icons.Default.Delete, contentDescription = "Delete Category", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(16.dp))
                        }
                    }
                }
            }
            if (expandedCategories[category] ?: true) {
                items(groupedTags[category] ?: emptyList(), key = { it.name }) { tag ->
                val isFavorite = tag.name == "Favourite"
                val isSelected = selectedTagNames.contains(tag.name)
                
                ListItem(
                    headlineContent = { Text(tag.name) },
                    supportingContent = { 
                        val count = songs.count { it.tags.contains(tag.name) }
                        val stateText = when (tag.state) {
                            TagState.INCLUDED -> stringResource(R.string.tag_included)
                            TagState.EXCLUDED -> stringResource(R.string.tag_excluded)
                            TagState.DISJUNCTION -> "Any of"
                            TagState.NONE -> ""
                        }
                        Text(stringResource(R.string.tag_summary, count, stateText))
                    },
                    leadingContent = { 
                        if (selectedTagNames.isNotEmpty() && !isFavorite) {
                            Checkbox(
                                checked = isSelected,
                                onCheckedChange = { viewModel.toggleTagSelection(tag.name) }
                            )
                        } else {
                            val icon = when (tag.state) {
                                TagState.INCLUDED -> Icons.Default.Check
                                TagState.EXCLUDED -> Icons.Default.Block
                                TagState.DISJUNCTION -> Icons.Default.DoneAll
                                TagState.NONE -> if (isFavorite) Icons.Default.Favorite else Icons.Default.Tag
                            }
                            val tint = when (tag.state) {
                                TagState.INCLUDED -> MaterialTheme.colorScheme.primary
                                TagState.EXCLUDED -> MaterialTheme.colorScheme.error
                                TagState.DISJUNCTION -> Color.Magenta
                                TagState.NONE -> if (isFavorite) Color.Red else LocalContentColor.current
                            }
                            Icon(icon, contentDescription = null, tint = tint)
                        }
                    },
                    trailingContent = {
                        Row {
                            IconButton(onClick = { viewModel.cycleTagState(tag.name) }) {
                                Icon(Icons.Default.Refresh, contentDescription = stringResource(R.string.acc_cycle_state))
                            }
                            if (!isFavorite) {
                                var showMenu by remember { mutableStateOf(false) }
                                Box {
                                    IconButton(onClick = { showMenu = true }) {
                                        Icon(Icons.Default.MoreVert, contentDescription = stringResource(R.string.acc_options))
                                    }
                                    DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                                        DropdownMenuItem(
                                            text = { Text(stringResource(R.string.action_rename)) },
                                            onClick = { 
                                                showMenu = false
                                                tagToEdit = tag 
                                            },
                                            leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null) }
                                        )
                                        DropdownMenuItem(
                                            text = { Text(stringResource(R.string.action_delete), color = MaterialTheme.colorScheme.error) },
                                            onClick = { 
                                                showMenu = false
                                                showDeleteDialog = tag 
                                            },
                                            leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error) }
                                        )
                                    }
                                }
                            }
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            if (selectedTagNames.isNotEmpty() && !isFavorite) {
                                viewModel.toggleTagSelection(tag.name)
                            } else {
                                viewModel.cycleTagState(tag.name)
                            }
                        }
                        .background(if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f) else Color.Transparent)
                )
            }
        }
    }
    item {
        var showChoiceDialog by remember { mutableStateOf(false) }
        var showAddTagDialog by remember { mutableStateOf(false) }
        var showAddCategoryDialog by remember { mutableStateOf(false) }

        if (showChoiceDialog) {
            AlertDialog(
                onDismissRequest = { showChoiceDialog = false },
                title = { Text("Create New") },
                text = { Text("What would you like to create?") },
                confirmButton = {
                    TextButton(onClick = { 
                        showAddTagDialog = true
                        showChoiceDialog = false
                    }) { Text("Tag") }
                },
                dismissButton = {
                    TextButton(onClick = { 
                        showAddCategoryDialog = true
                        showChoiceDialog = false
                    }) { Text("Category") }
                }
            )
        }

        if (showAddTagDialog) {
            TagEditDialog(
                viewModel = viewModel,
                title = stringResource(R.string.dialog_add_tag_title),
                initialName = "",
                onConfirm = { name, category ->
                    viewModel.addTag(name, category)
                    showAddTagDialog = false
                },
                onDismiss = { showAddTagDialog = false }
            )
        }
        
        if (showAddCategoryDialog) {
            var catName by remember { mutableStateOf("") }
            var step by remember { mutableStateOf(1) }
            val selectedTagsForCat = remember { mutableStateListOf<String>() }

            AlertDialog(
                onDismissRequest = { showAddCategoryDialog = false },
                title = { Text(if (step == 1) "Category Name" else "Assign Tags to $catName") },
                text = {
                    if (step == 1) {
                        TextField(
                            value = catName,
                            onValueChange = { catName = it },
                            placeholder = { Text("e.g. Genre, Mood...") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    } else {
                        Box(modifier = Modifier.heightIn(max = 400.dp)) {
                            LazyColumn {
                                items(tags.filter { it.name != "Favourite" }) { tag ->
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable { 
                                                if (selectedTagsForCat.contains(tag.name)) selectedTagsForCat.remove(tag.name)
                                                else selectedTagsForCat.add(tag.name)
                                            }
                                            .padding(vertical = 4.dp)
                                    ) {
                                        Checkbox(
                                            checked = selectedTagsForCat.contains(tag.name),
                                            onCheckedChange = { checked ->
                                                if (checked) selectedTagsForCat.add(tag.name)
                                                else selectedTagsForCat.remove(tag.name)
                                            }
                                        )
                                        Text(tag.name)
                                    }
                                }
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(
                        enabled = catName.isNotBlank(),
                        onClick = {
                            if (step == 1) {
                                step = 2
                            } else {
                                viewModel.assignTagsToCategory(selectedTagsForCat.toList(), catName)
                                showAddCategoryDialog = false
                            }
                        }
                    ) { Text(if (step == 1) "Next" else "Create") }
                },
                dismissButton = {
                    TextButton(onClick = { showAddCategoryDialog = false }) { Text("Cancel") }
                }
            )
        }

        OutlinedButton(
            onClick = { showChoiceDialog = true },
            modifier = Modifier.fillMaxWidth().padding(top = 16.dp)
        ) {
            Text("Create New Tag/Category")
        }
    }
}

    // Rename Dialog
    tagToEdit?.let { tag ->
        TagEditDialog(
            viewModel = viewModel,
            title = stringResource(R.string.dialog_rename_tag_title),
            initialName = tag.name,
            initialCategory = tag.category,
            onConfirm = { newName, category ->
                viewModel.renameTag(tag.name, newName, category)
                tagToEdit = null
            },
            onDismiss = { tagToEdit = null }
        )
    }

    // Delete Confirmation Dialog
    showDeleteDialog?.let { tag ->
        AlertDialog(
            onDismissRequest = { showDeleteDialog = null },
            title = { Text(stringResource(R.string.dialog_delete_tag_title)) },
            text = { Text(stringResource(R.string.dialog_delete_tag_confirm, tag.name)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteTag(tag.name)
                        showDeleteDialog = null
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) { Text(stringResource(R.string.action_delete)) }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = null }) { Text(stringResource(R.string.dialog_cancel)) }
            }
        )
    }
}

@Composable
fun TagEditDialog(
    viewModel: MusicViewModel,
    title: String,
    initialName: String,
    initialCategory: String = "Default",
    onConfirm: (String, String) -> Unit,
    onDismiss: () -> Unit
) {
    var name by remember { mutableStateOf(initialName) }
    var category by remember { mutableStateOf(initialCategory) }
    var expanded by remember { mutableStateOf(false) }
    val tags by viewModel.tags.collectAsState()
    val categories = remember(tags) { 
        tags.map { it.category }.distinct().sortedWith(compareByDescending<String> { it == "Default" }.thenBy { it })
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                TextField(
                    value = name,
                    onValueChange = { name = it },
                    placeholder = { Text(stringResource(R.string.hint_tag_name)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(16.dp))
                
                Text("Category", style = MaterialTheme.typography.labelMedium)
                Box(modifier = Modifier.fillMaxWidth()) {
                    OutlinedButton(
                        onClick = { expanded = true },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(category)
                        Spacer(modifier = Modifier.weight(1f))
                        Icon(Icons.Default.ArrowDropDown, contentDescription = null)
                    }
                    DropdownMenu(
                        expanded = expanded,
                        onDismissRequest = { expanded = false }
                    ) {
                        categories.forEach { cat ->
                            DropdownMenuItem(
                                text = { Text(cat) },
                                onClick = {
                                    category = cat
                                    expanded = false
                                }
                            )
                        }
                        HorizontalDivider()
                        var showCustomCat by remember { mutableStateOf(false) }
                        if (showCustomCat) {
                            var customCat by remember { mutableStateOf("") }
                            Row(modifier = Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                TextField(
                                    value = customCat,
                                    onValueChange = { customCat = it },
                                    placeholder = { Text("New...") },
                                    singleLine = true,
                                    modifier = Modifier.width(120.dp)
                                )
                                IconButton(onClick = {
                                    if (customCat.isNotBlank()) {
                                        category = customCat
                                        expanded = false
                                    }
                                }) { Icon(Icons.Default.Check, contentDescription = null) }
                            }
                        } else {
                            DropdownMenuItem(
                                text = { Text("Add New Category...") },
                                onClick = { showCustomCat = true }
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { if (name.isNotBlank()) onConfirm(name, category.ifBlank { "Default" }) },
                enabled = name.isNotBlank() && name != "Favourite"
            ) { Text(stringResource(R.string.action_save)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.dialog_cancel)) }
        }
    )
}
