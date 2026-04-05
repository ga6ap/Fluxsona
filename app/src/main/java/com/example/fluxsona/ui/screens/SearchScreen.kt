package com.example.fluxsona.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
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

enum class SearchMode { SCRAPE, LINK }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(viewModel: MusicViewModel) {
    var searchQuery by remember { mutableStateOf("") }
    var selectedSource by remember { mutableStateOf(SearchSource.YOUTUBE) }
    var searchMode by remember { mutableStateOf(SearchMode.SCRAPE) }
    val searchResults = remember { mutableStateListOf<Song>() }

    Column(modifier = Modifier.fillMaxSize()) {
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
                    searchResults.clear()
                    if (searchMode == SearchMode.SCRAPE) {
                        searchResults.add(Song(
                            id = "${selectedSource.name.lowercase()}_${System.currentTimeMillis()}",
                            title = "[$selectedSource] Scraped: $it",
                            artist = "Scraped from ${selectedSource.label}",
                            duration = "3:30",
                            thumbnailUri = null
                        ))
                    } else {
                        searchResults.add(Song(
                            id = "link_${System.currentTimeMillis()}",
                            title = "Linked Content",
                            artist = it,
                            duration = "Unknown",
                            thumbnailUri = null
                        ))
                    }
                },
                active = false,
                onActiveChange = {},
                placeholder = { 
                    Text(if (searchMode == SearchMode.SCRAPE) "Scrape ${selectedSource.label}..." else "Paste link here...") 
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
                    contentDescription = "Toggle Mode"
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
                        label = { Text(source.label) },
                        leadingIcon = if (selectedSource == source) {
                            { Text(source.icon, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold) }
                        } else null
                    )
                }
            }
        } else {
            Text(
                "Link Mode: Enter a URL to import directly",
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
