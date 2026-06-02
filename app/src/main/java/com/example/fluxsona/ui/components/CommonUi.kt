package com.example.fluxsona.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.SubcomposeAsyncImage
import com.example.fluxsona.data.model.Song

import com.example.fluxsona.R
import java.io.File

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SongItem(
    song: Song,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onLongClick: (() -> Unit)? = null,
    onMoreClick: (() -> Unit)? = null,
    onFavoriteClick: (() -> Unit)? = null,
    isFavorite: Boolean = false,
    onDragHandle: (@Composable () -> Unit)? = null,
    showCheckbox: Boolean = false,
    isSelected: Boolean = false,
    onSelectionChange: (Boolean) -> Unit = {},
    trailingContent: (@Composable () -> Unit)? = null
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            )
            .padding(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (showCheckbox) {
            Checkbox(
                checked = isSelected,
                onCheckedChange = onSelectionChange,
                modifier = Modifier.padding(end = 8.dp)
            )
        }

        onDragHandle?.invoke()

        // Thumbnail
        Box(contentAlignment = Alignment.Center) {
            SubcomposeAsyncImage(
                model = song.localThumbnailPath?.let { File(it) } ?: song.thumbnailUri,
                contentDescription = stringResource(R.string.acc_thumbnail),
                modifier = Modifier
                    .size(56.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(Color.Gray),
                contentScale = ContentScale.Crop,
                error = {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            Icons.Default.MusicNote, 
                            contentDescription = null, 
                            tint = Color.White,
                            modifier = Modifier.padding(12.dp)
                        )
                    }
                },
                loading = {
                    Box(contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp))
                    }
                }
            )
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
                text = song.artist,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (song.tags.isNotEmpty()) {
                Text(
                    text = song.tags.joinToString(", "),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        // Cache Status Indicators
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (onFavoriteClick != null) {
                IconButton(onClick = onFavoriteClick) {
                    Icon(
                        if (isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                        contentDescription = stringResource(R.string.player_favourite),
                        tint = if (isFavorite) Color.Red else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            if (song.cacheStatus.hasAudio) {
                Icon(
                    Icons.Default.DownloadDone,
                    contentDescription = stringResource(R.string.player_audio_cached),
                    modifier = Modifier.size(16.dp),
                    tint = Color.Green
                )
            }
            if (song.cacheStatus.hasThumbnail) {
                Icon(
                    Icons.Default.Image,
                    contentDescription = stringResource(R.string.player_thumbnail_cached),
                    modifier = Modifier.size(16.dp),
                    tint = Color(0xFFFFA500) // Orange
                )
            }
            if (song.cacheStatus.hasLyrics) {
                Icon(
                    Icons.Default.Description,
                    contentDescription = stringResource(R.string.player_lyrics_cached),
                    modifier = Modifier.size(16.dp),
                    tint = Color(0xFF9C27B0) // Purple
                )
            }
            if (song.cacheStatus.hasVideo) {
                Icon(
                    Icons.Default.Videocam,
                    contentDescription = stringResource(R.string.player_video_cached),
                    modifier = Modifier.size(16.dp),
                    tint = Color.Cyan
                )
            }
        }
        
        if (onMoreClick != null) {
            Box {
                IconButton(onClick = onMoreClick) {
                    Icon(Icons.Default.MoreVert, contentDescription = stringResource(R.string.player_more))
                }
                trailingContent?.invoke()
            }
        } else {
            trailingContent?.invoke()
        }
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

sealed class Screen(val route: String, val icon: ImageVector, val labelRes: Int) {
    object Home : Screen("home", Icons.Default.Home, R.string.nav_home)
    object Search : Screen("search", Icons.Default.Search, R.string.nav_search)
    object Library : Screen("library", Icons.Default.LibraryMusic, R.string.nav_library)
    object Settings : Screen("settings", Icons.Default.Settings, R.string.nav_settings)
}
