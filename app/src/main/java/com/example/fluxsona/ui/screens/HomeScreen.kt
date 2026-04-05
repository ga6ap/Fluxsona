package com.example.fluxsona.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.fluxsona.data.model.Song
import com.example.fluxsona.data.model.TagState
import com.example.fluxsona.ui.MusicViewModel
import com.example.fluxsona.ui.components.SongItem
import com.example.fluxsona.ui.components.TagEditorDialog

@Composable
fun HomeScreen(viewModel: MusicViewModel) {
    val filteredSongs by viewModel.filteredSongs.collectAsState()
    val tags by viewModel.tags.collectAsState()
    val tagFilterStates = viewModel.tagFilterStates
    var songToEditTags by remember { mutableStateOf<Song?>(null) }

    if (songToEditTags != null) {
        TagEditorDialog(
            song = songToEditTags!!,
            viewModel = viewModel,
            onDismiss = { songToEditTags = null }
        )
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Text(
            text = "Tags",
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(16.dp)
        )
        
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
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
                            TagState.NONE -> null
                        }
                    },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = when (filterState) {
                            TagState.INCLUDED -> MaterialTheme.colorScheme.primaryContainer
                            TagState.EXCLUDED -> MaterialTheme.colorScheme.errorContainer
                            else -> Color.Transparent
                        }
                    )
                )
            }
        }

        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item {
                Text(
                    text = "Songs",
                    style = MaterialTheme.typography.headlineMedium,
                    modifier = Modifier.padding(vertical = 16.dp)
                )
            }
            
            if (filteredSongs.isEmpty()) {
                item {
                    Text("No songs matching your filters.", style = MaterialTheme.typography.bodyMedium)
                }
            } else {
                items(filteredSongs) { song ->
                    SongItem(
                        song = song,
                        isFavorite = viewModel.isSongFavorite(song.id),
                        onFavoriteClick = { viewModel.toggleFavorite(song.id) },
                        onClick = { viewModel.playSong(song) },
                        onMoreClick = { songToEditTags = song }
                    )
                }
            }
        }
    }
}
