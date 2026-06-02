package com.example.fluxsona.ui.components

import androidx.compose.animation.*
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.Player
import coil.compose.SubcomposeAsyncImage
import com.example.fluxsona.R
import com.example.fluxsona.data.model.Song
import com.example.fluxsona.ui.MusicViewModel
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState
import android.content.Context
import android.content.Intent
import com.example.fluxsona.PlaybackService

enum class PlayerValue { Collapsed, Expanded }

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MiniPlayer(
    viewModel: MusicViewModel,
    onTogglePlay: () -> Unit,
    onExpand: () -> Unit,
    onSkipNext: () -> Unit,
    onSkipPrevious: () -> Unit
) {
    val songState by viewModel.currentSong.collectAsState()
    val song = songState ?: return
    val isPlaying = viewModel.isPlaying
    val queue = viewModel.currentQueue
    val currentIndex = queue.indexOfFirst { it.id == viewModel.currentSongId }.coerceAtLeast(0)

    val pagerState = rememberPagerState(initialPage = currentIndex, pageCount = { queue.size })

    LaunchedEffect(currentIndex) {
        if (pagerState.currentPage != currentIndex) {
            pagerState.scrollToPage(currentIndex)
        }
    }

    LaunchedEffect(pagerState.targetPage) {
        if (pagerState.targetPage != currentIndex && pagerState.targetPage in queue.indices) {
            if (pagerState.targetPage > currentIndex) onSkipNext()
            else onSkipPrevious()
        }
    }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .height(72.dp)
            .clickable { onExpand() },
        color = MaterialTheme.colorScheme.surfaceVariant,
        tonalElevation = 8.dp
    ) {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize(),
            userScrollEnabled = true
        ) { pageIndex ->
            val pageSong = queue.getOrNull(pageIndex) ?: song
            Row(
                modifier = Modifier
                    .padding(8.dp)
                    .fillMaxSize(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                SubcomposeAsyncImage(
                    model = pageSong.localThumbnailPath ?: pageSong.thumbnailUri,
                    contentDescription = null,
                    modifier = Modifier
                        .size(56.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(Color.Gray),
                    contentScale = ContentScale.Crop,
                    error = {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(Icons.Default.MusicNote, contentDescription = null, tint = Color.White)
                        }
                    },
                    loading = {
                        Box(contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp))
                        }
                    }
                )

                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = pageSong.title,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        modifier = Modifier.basicMarquee()
                    )
                    Text(
                        text = "${pageSong.artist} • ${formatDuration(pageSong.duration.toLongOrNull() ?: 0L)}",
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 1,
                        modifier = Modifier.basicMarquee(iterations = Int.MAX_VALUE)
                    )
                }

                IconButton(onClick = onSkipPrevious) {
                    Icon(Icons.Default.SkipPrevious, contentDescription = stringResource(R.string.acc_previous))
                }
                IconButton(onClick = onTogglePlay) {
                    Icon(
                        if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = stringResource(R.string.acc_play_pause)
                    )
                }
                IconButton(onClick = onSkipNext) {
                    Icon(Icons.Default.SkipNext, contentDescription = stringResource(R.string.acc_next))
                }
            }
        }
    }
}


@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun ExpandedPlayer(
    viewModel: MusicViewModel,
    onCollapse: () -> Unit
) {
    val context = LocalContext.current
    val songState by viewModel.currentSong.collectAsState()
    val song = songState ?: return

    val isPlaying = viewModel.isPlaying
    val currentPosition = viewModel.currentPosition
    val duration = viewModel.duration
    val repeatMode = viewModel.repeatMode

    val lazyListState = rememberLazyListState()
    val reorderableState = rememberReorderableLazyListState(lazyListState, scrollThreshold = 150.dp) { from, to ->
        if (from.index in viewModel.currentQueue.indices && to.index in viewModel.currentQueue.indices) {
            viewModel.moveQueueItem(from.index, to.index)
        }
    }
    val haptic = LocalHapticFeedback.current

    // Local state for smooth slider dragging
    var sliderDraggingValue by remember { mutableStateOf<Float?>(null) }
    val sliderValue = sliderDraggingValue ?: if (duration > 0) currentPosition.toFloat() / duration else 0f

    var showLyrics by remember { mutableStateOf(false) }

    // Auto-scroll to current song when expanded or when song changes
    LaunchedEffect(viewModel.currentSongId) {
        val index = viewModel.currentQueue.indexOfFirst { it.id == viewModel.currentSongId }
        if (index != -1) {
            lazyListState.animateScrollToItem(index)
        }
    }

    if (showLyrics) {
        LyricsDialog(
            song = song,
            viewModel = viewModel,
            onDismiss = { showLyrics = false }
        )
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
        ) {
            // Header Row - Always visible and active
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onCollapse) {
                    Icon(Icons.Default.KeyboardArrowDown, contentDescription = stringResource(R.string.acc_collapse))
                }

                if (viewModel.isQueueMaximized) {
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = 12.dp)
                    ) {
                        Text(
                            text = song.title,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = song.artist,
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                } else {
                    Spacer(modifier = Modifier.weight(1f))
                }

                IconButton(onClick = { viewModel.isQueueMaximized = !viewModel.isQueueMaximized }) {
                    Icon(
                        if (viewModel.isQueueMaximized) Icons.Default.ExpandMore else Icons.Default.ExpandLess,
                        contentDescription = "Toggle Queue Size"
                    )
                }
            }

            val playerWeight by animateFloatAsState(
                targetValue = if (viewModel.isQueueMaximized) 0.0001f else 1f,
                label = "playerWeight"
            )
            val queueWeight by animateFloatAsState(
                targetValue = if (viewModel.isQueueMaximized) 1f else 0.45f,
                label = "queueWeight"
            )

            // Main Content Area (Player)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(playerWeight)
                    .graphicsLayer {
                        alpha = if (playerWeight < 0.2f) 0f else (playerWeight - 0.2f) / 0.8f
                    },
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                if (playerWeight > 0.2f) {
                    val queue = viewModel.currentQueue
                    val currentIndex = queue.indexOfFirst { it.id == viewModel.currentSongId }.coerceAtLeast(0)
                    val pagerState = rememberPagerState(initialPage = currentIndex, pageCount = { queue.size })

                    LaunchedEffect(currentIndex) {
                        if (pagerState.currentPage != currentIndex) {
                            pagerState.scrollToPage(currentIndex)
                        }
                    }

                    LaunchedEffect(pagerState.targetPage) {
                        if (pagerState.targetPage != currentIndex && pagerState.targetPage in queue.indices) {
                            if (pagerState.targetPage > currentIndex) viewModel.skipNext()
                            else viewModel.skipPrevious()
                        }
                    }

                    HorizontalPager(
                        state = pagerState,
                        modifier = Modifier.fillMaxWidth().weight(1f),
                        userScrollEnabled = playerWeight > 0.8f,
                        contentPadding = PaddingValues(top = 8.dp)
                    ) { pageIndex ->
                        val pageSong = queue.getOrNull(pageIndex) ?: song
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            SubcomposeAsyncImage(
                                model = pageSong.localThumbnailPath ?: pageSong.thumbnailUri,
                                contentDescription = null,
                                modifier = Modifier
                                    .weight(1f)
                                    .aspectRatio(1f)
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(Color.Gray),
                                contentScale = ContentScale.Crop,
                                error = {
                                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                        Icon(Icons.Default.MusicNote, contentDescription = null, tint = Color.White, modifier = Modifier.size(64.dp))
                                    }
                                },
                                loading = {
                                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                        CircularProgressIndicator(modifier = Modifier.size(48.dp))
                                    }
                                }
                            )

                            Spacer(modifier = Modifier.height(16.dp))

                            Text(
                                text = pageSong.title,
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                modifier = Modifier.basicMarquee()
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = pageSong.artist,
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    modifier = Modifier.basicMarquee(iterations = Int.MAX_VALUE)
                                )
                                val isFavorite = pageSong.isFavorite
                                IconButton(onClick = { viewModel.toggleFavorite(pageSong.id) }) {
                                    Icon(
                                        if (isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                                        contentDescription = stringResource(R.string.player_favourite),
                                        tint = if (isFavorite) Color.Red else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }

                    Column(
                        modifier = Modifier
                            .padding(start = 24.dp, end = 24.dp, bottom = 8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Slider(
                            value = sliderValue.coerceIn(0f, 1f),
                            onValueChange = { sliderDraggingValue = it },
                            onValueChangeFinished = {
                                sliderDraggingValue?.let { viewModel.seekTo((it * duration).toLong()) }
                                sliderDraggingValue = null
                            }
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            val currentPos = if (sliderDraggingValue != null) (sliderDraggingValue!! * duration).toLong() else currentPosition
                            Text(formatDuration(currentPos), style = MaterialTheme.typography.labelSmall)
                            Text(formatDuration(duration), style = MaterialTheme.typography.labelSmall)
                        }

                        Spacer(modifier = Modifier.height(4.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceEvenly,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconButton(onClick = { viewModel.toggleShuffleMode() }) {
                                Icon(Icons.Default.Shuffle, contentDescription = stringResource(R.string.acc_shuffle),
                                    tint = if (viewModel.isShuffleEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
                            }
                            IconButton(onClick = { viewModel.seekBackward() }) {
                                SkipIcon(
                                    seconds = viewModel.skipBackwardSeconds,
                                    isForward = false,
                                    contentDescription = stringResource(R.string.acc_back_seconds, viewModel.skipBackwardSeconds)
                                )
                            }
                            FilledIconButton(
                                onClick = { viewModel.togglePlayPause() },
                                modifier = Modifier.size(56.dp)
                            ) {
                                Icon(
                                    if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                    contentDescription = stringResource(R.string.acc_play_pause),
                                    modifier = Modifier.size(32.dp)
                                )
                            }
                            IconButton(onClick = { viewModel.seekForward() }) {
                                SkipIcon(
                                    seconds = viewModel.skipForwardSeconds,
                                    isForward = true,
                                    contentDescription = stringResource(R.string.acc_forward_seconds, viewModel.skipForwardSeconds)
                                )
                            }
                            IconButton(onClick = { viewModel.toggleRepeatMode() }) {
                                Icon(
                                    when (repeatMode) {
                                        Player.REPEAT_MODE_ONE -> Icons.Default.RepeatOne
                                        Player.REPEAT_MODE_ALL -> Icons.Default.Repeat
                                        else -> Icons.Default.Repeat
                                    },
                                    contentDescription = stringResource(R.string.acc_repeat),
                                    tint = if (repeatMode != Player.REPEAT_MODE_OFF) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(4.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceEvenly,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            var showSoundMenu by remember { mutableStateOf(false) }
                            Box {
                                IconButton(onClick = { showSoundMenu = true }) {
                                    Icon(
                                        when (viewModel.currentSoundOption) {
                                            "Bass Boost" -> Icons.Default.Audiotrack
                                            "Voice Boost" -> Icons.Default.RecordVoiceOver
                                            else -> Icons.Default.GraphicEq
                                        },
                                        contentDescription = "Sound Options",
                                        tint = if (viewModel.currentSoundOption != "Default") MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                DropdownMenu(expanded = showSoundMenu, onDismissRequest = { showSoundMenu = false }) {
                                    listOf("Default", "Bass Boost", "Voice Boost").forEach { option ->
                                        DropdownMenuItem(
                                            text = { Text(option) },
                                            onClick = {
                                                viewModel.setSoundOption(context, option)
                                                showSoundMenu = false
                                            },
                                            trailingIcon = { if (viewModel.currentSoundOption == option) Icon(Icons.Default.Check, contentDescription = null) }
                                        )
                                    }
                                }
                            }

                            IconButton(onClick = { viewModel.togglePlaybackSpeed() }) {
                                Text("${viewModel.playbackSpeed}x", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            IconButton(onClick = { viewModel.skipPrevious() }) {
                                Icon(Icons.Default.SkipPrevious, contentDescription = stringResource(R.string.acc_previous), modifier = Modifier.size(32.dp))
                            }
                            IconButton(onClick = { viewModel.skipNext() }) {
                                Icon(Icons.Default.SkipNext, contentDescription = stringResource(R.string.acc_next), modifier = Modifier.size(32.dp))
                            }

                            var showVolumeDialog by remember { mutableStateOf(false) }
                            if (showVolumeDialog) {
                                VolumeDialog(viewModel = viewModel, onDismiss = { showVolumeDialog = false })
                            }
                            IconButton(onClick = { showVolumeDialog = true }) {
                                Icon(
                                    imageVector = if (viewModel.isMuted) Icons.AutoMirrored.Filled.VolumeOff else Icons.AutoMirrored.Filled.VolumeUp,
                                    contentDescription = "Volume",
                                    tint = if (viewModel.isMuted) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            IconButton(onClick = { showLyrics = true }) {
                                Icon(
                                    imageVector = if (song.cacheStatus.hasLyrics) Icons.Default.Lyrics else Icons.Default.AddComment,
                                    contentDescription = if (song.cacheStatus.hasLyrics) "Show Lyrics" else "Add Lyrics",
                                    tint = if (song.cacheStatus.hasLyrics) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }

            // Scrollable Queue Section
            Column(
                modifier = Modifier
                    .weight(queueWeight)
                    .pointerInput(Unit) {
                        detectVerticalDragGestures { change, dragAmount ->
                            if (dragAmount < -10 && !viewModel.isQueueMaximized) {
                                viewModel.isQueueMaximized = true
                                change.consume()
                            }
                            else if (dragAmount > 10 && viewModel.isQueueMaximized &&
                                lazyListState.firstVisibleItemIndex == 0 &&
                                lazyListState.firstVisibleItemScrollOffset == 0) {
                                viewModel.isQueueMaximized = false
                                change.consume()
                            }
                        }
                    }
            ) {
                // Visual Handle + Persistent Queue Header
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .pointerInput(Unit) {
                            detectVerticalDragGestures { change, dragAmount ->
                                if (dragAmount > 8 && viewModel.isQueueMaximized) {
                                    viewModel.isQueueMaximized = false
                                    change.consume()
                                } else if (dragAmount < -8 && !viewModel.isQueueMaximized) {
                                    viewModel.isQueueMaximized = true
                                    change.consume()
                                }
                            }
                        }
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 12.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Surface(
                            modifier = Modifier.width(36.dp).height(4.dp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f),
                            shape = RoundedCornerShape(2.dp)
                        ) {}
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 24.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            stringResource(R.string.header_up_next),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )

                        var showSongToEditTags by remember { mutableStateOf<Song?>(null) }
                        if (showSongToEditTags != null) {
                            TagEditorDialog(
                                song = showSongToEditTags!!,
                                viewModel = viewModel,
                                onDismiss = { showSongToEditTags = null }
                            )
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(onClick = { viewModel.clearQueue() }) {
                                Icon(Icons.Default.ClearAll, contentDescription = "Clear Queue", tint = Color.Red)
                            }

                            IconButton(onClick = { viewModel.syncQueueWithFiltered() }) {
                                Icon(Icons.Default.Sync, contentDescription = "Sync Queue with Library")
                            }

                            IconButton(onClick = { showSongToEditTags = song }) {
                                Icon(Icons.Default.Edit, contentDescription = stringResource(R.string.menu_edit_tags))
                            }
                        }
                    }
                }

                LazyColumn(
                    state = lazyListState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 24.dp, start = 24.dp, end = 24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    itemsIndexed(viewModel.currentQueue, key = { _, song -> song.id }) { _, queueSong ->
                        val isCurrentPlaying = viewModel.currentSongId == queueSong.id

                        @Suppress("DEPRECATION")
                        val dismissState = rememberSwipeToDismissBoxState(
                            confirmValueChange = { value ->
                                if (value == SwipeToDismissBoxValue.EndToStart) {
                                    viewModel.removeFromQueue(queueSong.id)
                                    true
                                } else false
                            },
                            positionalThreshold = { it * 0.6f }
                        )

                        LaunchedEffect(queueSong.id) {
                            if (dismissState.currentValue != SwipeToDismissBoxValue.Settled) {
                                dismissState.snapTo(SwipeToDismissBoxValue.Settled)
                            }
                        }

                        ReorderableItem(
                            state = reorderableState,
                            key = queueSong.id
                        ) {
                            SwipeToDismissBox(
                                state = dismissState,
                                modifier = Modifier.fillMaxWidth(),
                                backgroundContent = {
                                    val isDismissing = dismissState.dismissDirection == SwipeToDismissBoxValue.EndToStart
                                    Box(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .background(if (isDismissing) Color.Red else Color.Transparent)
                                            .padding(horizontal = 24.dp),
                                        contentAlignment = Alignment.CenterEnd
                                    ) {
                                        if (isDismissing) {
                                            Icon(
                                                Icons.Default.Delete,
                                                contentDescription = stringResource(R.string.acc_remove_from_queue),
                                                tint = Color.White
                                            )
                                        }
                                    }
                                },
                                enableDismissFromStartToEnd = false
                            ) {
                                Surface(
                                    modifier = Modifier.fillMaxWidth(),
                                    color = if (isCurrentPlaying) MaterialTheme.colorScheme.surfaceVariant
                                    else MaterialTheme.colorScheme.surface,
                                    tonalElevation = if (isCurrentPlaying) 2.dp else 0.dp
                                ) {
                                    SongItem(
                                        song = queueSong,
                                        isFavorite = queueSong.isFavorite,
                                        onFavoriteClick = { viewModel.toggleFavorite(queueSong.id) },
                                        onClick = { viewModel.playSong(context, queueSong, viewModel.currentQueue.toList()) },
                                        onDragHandle = {
                                            Icon(
                                                Icons.Default.DragHandle,
                                                contentDescription = stringResource(R.string.acc_drag_to_reorder),
                                                modifier = Modifier.padding(horizontal = 8.dp).draggableHandle(
                                                    onDragStarted = { haptic.performHapticFeedback(HapticFeedbackType.LongPress) }
                                                ),
                                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LyricsDialog(
    song: Song,
    viewModel: MusicViewModel,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val initialLyrics = remember(song) { viewModel.getLyrics(song) ?: "" }
    var isEditing by remember { mutableStateOf(initialLyrics.isBlank()) }
    var editedLyrics by remember { mutableStateOf(initialLyrics) }
    var textSize by remember { mutableStateOf(viewModel.lyricsTextSize) }

    BasicAlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier
            .fillMaxSize()
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clickable { onDismiss() },
            contentAlignment = Alignment.Center
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth(0.95f)
                    .fillMaxHeight(0.85f)
                    .clickable(enabled = false) { },
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surface
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (isEditing) "Edit Lyrics" else "Lyrics",
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (isEditing) {
                                IconButton(onClick = {
                                    viewModel.saveLyrics(song, context, editedLyrics)
                                    isEditing = false
                                }) {
                                    Icon(Icons.Default.Save, contentDescription = "Save")
                                }
                            } else {
                                IconButton(onClick = { isEditing = true }) {
                                    Icon(Icons.Default.Edit, contentDescription = "Edit")
                                }
                            }
                            IconButton(onClick = onDismiss) {
                                Icon(Icons.Default.Close, contentDescription = "Close")
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Text(
                        text = "${song.title} - ${song.artist}",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary
                    )

                    if (!isEditing) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(vertical = 8.dp)
                        ) {
                            Icon(Icons.Default.FormatSize, contentDescription = null, modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Slider(
                                value = textSize,
                                onValueChange = {
                                    textSize = it
                                    viewModel.updateLyricsTextSize(it)
                                },
                                valueRange = 12f..48f,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Box(modifier = Modifier.weight(1f)) {
                        if (isEditing) {
                            OutlinedTextField(
                                value = editedLyrics,
                                onValueChange = { editedLyrics = it },
                                modifier = Modifier.fillMaxSize(),
                                placeholder = { Text("Type lyrics here...") }
                            )
                        } else {
                            LazyColumn(modifier = Modifier.fillMaxSize()) {
                                item {
                                    Text(
                                        text = editedLyrics.ifBlank { "No lyrics found." },
                                        style = MaterialTheme.typography.bodyLarge.copy(fontSize = textSize.sp),
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}


@Composable
fun SkipIcon(
    seconds: Int,
    isForward: Boolean,
    contentDescription: String,
    modifier: Modifier = Modifier
) {
    val icon = if (isForward) {
        when (seconds) {
            5 -> Icons.Default.Forward5
            10 -> Icons.Default.Forward10
            30 -> Icons.Default.Forward30
            else -> null
        }
    } else {
        when (seconds) {
            5 -> Icons.Default.Replay5
            10 -> Icons.Default.Replay10
            30 -> Icons.Default.Replay30
            else -> null
        }
    }

    if (icon != null) {
        Icon(icon, contentDescription = contentDescription, modifier = modifier)
    } else {
        Box(contentAlignment = Alignment.Center, modifier = modifier) {
            Icon(
                imageVector = if (isForward) Icons.AutoMirrored.Filled.RotateRight else Icons.AutoMirrored.Filled.RotateLeft,
                contentDescription = contentDescription
            )
            Text(
                text = seconds.toString(),
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(top = 3.dp)
            )
        }
    }
}


private fun formatDuration(millis: Long): String {
    val totalSeconds = millis / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return String.format(java.util.Locale.getDefault(), "%d:%02d", minutes, seconds)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VolumeDialog(
    viewModel: MusicViewModel,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    BasicAlertDialog(
        onDismissRequest = onDismiss
    ) {
        Surface(
            modifier = Modifier.width(300.dp),
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Volume Control",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )

                Spacer(modifier = Modifier.height(24.dp))

                Slider(
                    value = viewModel.volume,
                    onValueChange = { viewModel.setVolumeValue(it) },
                    valueRange = 0f..5f,
                    enabled = !viewModel.isMuted,
                    modifier = Modifier.fillMaxWidth(),
                    colors = SliderDefaults.colors(
                        disabledThumbColor = MaterialTheme.colorScheme.error,
                        disabledActiveTrackColor = MaterialTheme.colorScheme.error,
                        disabledInactiveTrackColor = MaterialTheme.colorScheme.error.copy(alpha = 0.24f)
                    )
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        val volumePercent = (viewModel.volume * 100).toInt()
                        Text(
                            text = "$volumePercent%",
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (volumePercent > 100) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
                        )
                        if (viewModel.isMuted) {
                            Spacer(modifier = Modifier.width(8.dp))
                            Icon(
                                Icons.Default.Lock,
                                contentDescription = "Locked",
                                modifier = Modifier.size(16.dp),
                                tint = MaterialTheme.colorScheme.error
                            )
                        }
                    }

                    IconButton(onClick = { viewModel.toggleMute(context) }) {
                        Icon(
                            imageVector = if (viewModel.isMuted) Icons.AutoMirrored.Filled.VolumeOff else Icons.AutoMirrored.Filled.VolumeUp,
                            contentDescription = if (viewModel.isMuted) "Unmute" else "Mute",
                            tint = if (viewModel.isMuted) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier.align(Alignment.End)
                ) {
                    Text("Close")
                }
            }
        }
    }
}
