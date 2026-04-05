package com.example.fluxsona.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.fluxsona.data.model.Song
import com.example.fluxsona.ui.MusicViewModel
import com.example.fluxsona.ui.components.SongItem

enum class SearchSource(val label: String, val icon: String) {
    YOUTUBE("YouTube", "YT"),
    YOUTUBE_MUSIC("Music", "YTM"),
    SPOTIFY("Spotify", "SP"),
    APPLE_MUSIC("Apple", "AM"),
    SOUNDCLOUD("SoundCloud", "SC")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(viewModel: MusicViewModel) {
    var searchQuery by remember { mutableStateOf("") }
    var selectedSource by remember { mutableStateOf(SearchSource.YOUTUBE) }
    val searchResults = remember { mutableStateListOf<Song>() }

    Column(modifier = Modifier.fillMaxSize()) {
        SearchBar(
            query = searchQuery,
            onQueryChange = { searchQuery = it },
            onSearch = { 
                // Simulate search based on source
                searchResults.clear()
                searchResults.add(Song(
                    id = "${selectedSource.name.lowercase()}_${System.currentTimeMillis()}",
                    title = "[$selectedSource] Result for $it",
                    artist = "Found on ${selectedSource.label}",
                    duration = "3:30",
                    thumbnailUri = null
                ))
            },
            active = false,
            onActiveChange = {},
            placeholder = { Text("Search in ${selectedSource.label}...") },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) { }

        LazyRow(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(SearchSource.values()) { source ->
                FilterChip(
                    selected = selectedSource == source,
                    onClick = { selectedSource = source },
                    label = { Text(source.label) },
                    leadingIcon = if (selectedSource == source) {
                        { Text(source.icon, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold) }
                    } else null
                )
            }
        }

        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(searchResults) { song ->
                SongItem(
                    song = song,
                    onClick = { 
                        viewModel.importSong(song)
                    }
                )
            }
        }
    }
}
