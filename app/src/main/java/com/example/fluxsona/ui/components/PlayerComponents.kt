package com.example.fluxsona.ui.components

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.media3.common.Player
import com.example.fluxsona.data.model.Song
import java.util.Locale
import com.example.fluxsona.ui.MusicViewModel

@Composable
fun MiniPlayer(
    song: Song,
    isPlaying: Boolean,
    onTogglePlay: () -> Unit,
    onExpand: () -> Unit,
    onSkipNext: () -> Unit,
    onSkipPrevious: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .height(72.dp)
            .pointerInput(Unit) {
                detectVerticalDragGestures { change, dragAmount ->
                    if (dragAmount < -10) {
                        onExpand()
                        change.consume()
                    }
                }
            }
            .clickable { onExpand() },
        color = MaterialTheme.colorScheme.surfaceVariant,
        tonalElevation = 8.dp
    ) {
        Row(
            modifier = Modifier
                .padding(8.dp)
                .fillMaxSize(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(Color.Gray),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.MusicNote, contentDescription = null, tint = Color.White)
            }
            
            Spacer(modifier = Modifier.width(12.dp))
            
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = song.title,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "${song.artist} • ${formatDuration(song.duration.toLongOrNull() ?: 0L)}",
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            IconButton(onClick = onSkipPrevious) {
                Icon(Icons.Default.SkipPrevious, contentDescription = "Previous")
            }
            IconButton(onClick = onTogglePlay) {
                Icon(
                    if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                    contentDescription = "Play/Pause"
                )
            }
            IconButton(onClick = onSkipNext) {
                Icon(Icons.Default.SkipNext, contentDescription = "Next")
            }
        }
    }
}

@Composable
fun ExpandedPlayer(
    viewModel: MusicViewModel,
    onCollapse: () -> Unit
) {
    val songState by viewModel.currentSong.collectAsState()
    val song = songState ?: return

    val isPlaying = viewModel.isPlaying
    val currentPosition = viewModel.currentPosition
    val duration = viewModel.duration
    val repeatMode = viewModel.repeatMode

    LaunchedEffect(isPlaying) {
        if (isPlaying) {
            while (true) {
                viewModel.updateProgress()
                kotlinx.coroutines.delay(1000L)
            }
        }
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .pointerInput(Unit) {
                        detectVerticalDragGestures { change, dragAmount ->
                            if (dragAmount > 10) {
                                onCollapse()
                                change.consume()
                            }
                        }
                    }
            ) {
                IconButton(
                    onClick = onCollapse,
                    modifier = Modifier.padding(16.dp)
                ) {
                    Icon(Icons.Default.KeyboardArrowDown, contentDescription = "Collapse")
                }
            }

            LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                item {
                    Box(
                        modifier = Modifier
                            .aspectRatio(1f)
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(Color.Gray)
                            .pointerInput(Unit) {
                                detectVerticalDragGestures { change, dragAmount ->
                                    if (dragAmount > 10) {
                                        onCollapse()
                                        change.consume()
                                    }
                                }
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.MusicNote,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(128.dp)
                        )
                    }
                    
                    Spacer(modifier = Modifier.height(32.dp))
                    
                    Text(
                        text = song.title,
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = song.artist,
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        val isFavorite = viewModel.isSongFavorite(song.id)
                        IconButton(onClick = { viewModel.toggleFavorite(song.id) }) {
                            Icon(
                                if (isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                                contentDescription = "Favorite",
                                tint = if (isFavorite) Color.Red else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(32.dp))
                    
                    Slider(
                        value = if (duration > 0) currentPosition.toFloat() / duration else 0f,
                        onValueChange = { viewModel.seekTo((it * duration).toLong()) }
                    )
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(formatDuration(currentPosition), style = MaterialTheme.typography.labelSmall)
                        Text(formatDuration(duration), style = MaterialTheme.typography.labelSmall)
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TextButton(onClick = { viewModel.togglePlaybackSpeed() }) {
                            Text("${viewModel.playbackSpeed}x", fontWeight = FontWeight.Bold)
                        }
                        IconButton(onClick = { viewModel.shuffleQueue() }) {
                            Icon(
                                Icons.Default.Shuffle,
                                contentDescription = "Shuffle"
                            )
                        }
                        IconButton(onClick = { viewModel.seekBackward() }) {
                            Icon(Icons.Default.Replay10, contentDescription = "Back 10s")
                        }
                        IconButton(onClick = { viewModel.skipPrevious() }) {
                            Icon(Icons.Default.SkipPrevious, contentDescription = "Previous")
                        }
                        FilledIconButton(
                            onClick = { viewModel.togglePlayPause() },
                            modifier = Modifier.size(64.dp)
                        ) {
                            Icon(
                                if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                contentDescription = "Play/Pause",
                                modifier = Modifier.size(32.dp)
                            )
                        }
                        IconButton(onClick = { viewModel.skipNext() }) {
                            Icon(Icons.Default.SkipNext, contentDescription = "Next")
                        }
                        IconButton(onClick = { viewModel.seekForward() }) {
                            Icon(Icons.Default.Forward10, contentDescription = "Forward 10s")
                        }
                        IconButton(onClick = { viewModel.toggleRepeatMode() }) {
                            Icon(
                                when (repeatMode) {
                                    Player.REPEAT_MODE_ONE -> Icons.Default.RepeatOne
                                    Player.REPEAT_MODE_ALL -> Icons.Default.Repeat
                                    else -> Icons.Default.Repeat
                                },
                                contentDescription = "Repeat",
                                tint = if (repeatMode != Player.REPEAT_MODE_OFF) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(32.dp))
                    
                    Text(
                        "Up Next",
                        style = MaterialTheme.typography.titleLarge,
                        modifier = Modifier.fillMaxWidth(),
                        fontWeight = FontWeight.Bold
                    )
                    
                    Spacer(modifier = Modifier.height(16.dp))
                }
                
                itemsIndexed(viewModel.currentQueue, key = { _, song -> song.id }) { index, queueSong ->
                    var offsetY by remember { mutableStateOf(0f) }
                    val dismissState = rememberSwipeToDismissBoxState()
                    
                    LaunchedEffect(dismissState.currentValue) {
                        if (dismissState.currentValue == SwipeToDismissBoxValue.EndToStart) {
                            viewModel.removeFromQueue(queueSong.id)
                        }
                    }

                    SwipeToDismissBox(
                        state = dismissState,
                        modifier = Modifier
                            .graphicsLayer { translationY = offsetY }
                            .zIndex(if (offsetY != 0f) 1f else 0f),
                        backgroundContent = {
                            val color = when (dismissState.dismissDirection) {
                                SwipeToDismissBoxValue.EndToStart -> Color.Red.copy(alpha = 0.8f)
                                else -> Color.Transparent
                            }
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(color)
                                    .padding(horizontal = 20.dp),
                                contentAlignment = Alignment.CenterEnd
                            ) {
                                Icon(
                                    Icons.Default.Delete,
                                    contentDescription = "Remove from queue",
                                    tint = Color.White
                                )
                            }
                        },
                        enableDismissFromStartToEnd = false
                    ) {
                        SongItem(
                            song = queueSong,
                            isFavorite = viewModel.isSongFavorite(queueSong.id),
                            onFavoriteClick = { viewModel.toggleFavorite(queueSong.id) },
                            onClick = { viewModel.playSong(queueSong) },
                            modifier = Modifier
                                .background(MaterialTheme.colorScheme.background),
                            onDragHandle = {
                                Icon(
                                    Icons.Default.DragHandle,
                                    contentDescription = "Drag to reorder",
                                    modifier = Modifier
                                        .padding(horizontal = 8.dp)
                                        .pointerInput(Unit) {
                                            detectDragGestures(
                                                onDrag = { change, dragAmount ->
                                                    change.consume()
                                                    offsetY += dragAmount.y
                                                },
                                                onDragEnd = {
                                                    val itemHeight = 72.dp.toPx()
                                                    val moveIndex = (offsetY / itemHeight).toInt()
                                                    if (moveIndex != 0) {
                                                        viewModel.moveQueueItem(
                                                            index,
                                                            (index + moveIndex).coerceIn(0, viewModel.currentQueue.size - 1)
                                                        )
                                                    }
                                                    offsetY = 0f
                                                },
                                                onDragCancel = {
                                                    offsetY = 0f
                                                }
                                            )
                                        },
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

private fun formatDuration(millis: Long): String {
    val totalSeconds = millis / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return String.format(Locale.getDefault(), "%d:%02d", minutes, seconds)
}
