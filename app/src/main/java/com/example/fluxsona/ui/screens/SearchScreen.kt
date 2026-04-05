package com.example.fluxsona.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.fluxsona.data.model.Song
import com.example.fluxsona.ui.MusicViewModel
import com.example.fluxsona.ui.components.SongItem

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(viewModel: MusicViewModel) {
    var searchQuery by remember { mutableStateOf("") }
    val searchResults = remember { mutableStateListOf<Song>() }

    Column(modifier = Modifier.fillMaxSize()) {
        SearchBar(
            query = searchQuery,
            onQueryChange = { searchQuery = it },
            onSearch = { 
                // Simulate search
                searchResults.clear()
                searchResults.add(Song(
                    id = "scraped_${System.currentTimeMillis()}",
                    title = "Result for $it",
                    artist = "Scraped Artist",
                    duration = "3:00",
                    thumbnailUri = null
                ))
            },
            active = false,
            onActiveChange = {},
            placeholder = { Text("Search YouTube, Spotify, etc...") },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) { }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(searchResults) { song ->
                SongItem(
                    song = song,
                    onClick = { 
                        viewModel.importSong(song)
                        // Maybe show a snackbar or toast
                    }
                )
            }
        }
    }
}
