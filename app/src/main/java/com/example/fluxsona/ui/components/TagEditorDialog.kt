package com.example.fluxsona.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.fluxsona.data.model.Song
import com.example.fluxsona.ui.MusicViewModel

@Composable
fun TagEditorDialog(
    song: Song,
    viewModel: MusicViewModel,
    onDismiss: () -> Unit
) {
    val allTags by viewModel.tags.collectAsState()
    val songTags = remember { mutableStateListOf<String>().apply { addAll(song.tags) } }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit Tags: ${song.title}") },
        text = {
            Column(modifier = Modifier.fillMaxWidth().heightIn(max = 400.dp)) {
                LazyColumn {
                    items(allTags) { tag ->
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(
                                checked = songTags.contains(tag.name),
                                onCheckedChange = { checked ->
                                    if (checked) songTags.add(tag.name)
                                    else songTags.remove(tag.name)
                                }
                            )
                            Text(tag.name)
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = {
                viewModel.updateSongTags(song.id, songTags.toList())
                onDismiss()
            }) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
