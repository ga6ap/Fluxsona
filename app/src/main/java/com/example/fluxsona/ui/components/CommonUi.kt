package com.example.fluxsona.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.fluxsona.data.model.Song

@Composable
fun SongItem(
    song: Song,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onMoreClick: (() -> Unit)? = null,
    onFavoriteClick: (() -> Unit)? = null,
    isFavorite: Boolean = false,
    onDragHandle: (@Composable () -> Unit)? = null
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        onDragHandle?.invoke()

        // Thumbnail placeholder
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
                        contentDescription = "Favorite",
                        tint = if (isFavorite) Color.Red else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            if (song.cacheStatus.hasAudio) {
                Icon(
                    Icons.Default.DownloadDone,
                    contentDescription = "Audio cached",
                    modifier = Modifier.size(16.dp),
                    tint = Color.Green
                )
            }
            if (song.cacheStatus.hasVideo) {
                Icon(
                    Icons.Default.Videocam,
                    contentDescription = "Video cached",
                    modifier = Modifier.size(16.dp),
                    tint = Color.Cyan
                )
            }
        }
        
        IconButton(onClick = { onMoreClick?.invoke() }) {
            Icon(Icons.Default.MoreVert, contentDescription = "More")
        }
    }
}

sealed class Screen(val route: String, val icon: ImageVector, val label: String) {
    object Home : Screen("home", Icons.Default.Home, "Home")
    object Search : Screen("search", Icons.Default.Search, "Search")
    object Library : Screen("library", Icons.Default.LibraryMusic, "Library")
    object Settings : Screen("settings", Icons.Default.Settings, "Settings")
}
