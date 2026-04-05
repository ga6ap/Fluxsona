package com.example.fluxsona.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.fluxsona.data.model.Tag
import com.example.fluxsona.data.model.TagState
import com.example.fluxsona.ui.MusicViewModel

@Composable
fun LibraryScreen(viewModel: MusicViewModel) {
    Column(modifier = Modifier.fillMaxSize()) {
        Text(
            text = "Tags",
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.padding(16.dp)
        )
        TagList(viewModel)
    }
}

@Composable
fun TagList(viewModel: MusicViewModel) {
    val tags by viewModel.tags.collectAsState()
    val songs by viewModel.songs.collectAsState()
    var tagToEdit by remember { mutableStateOf<Tag?>(null) }
    var showDeleteDialog by remember { mutableStateOf<Tag?>(null) }

    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(tags) { tag ->
            val isFavorites = tag.name == "Favorites"
            
            ListItem(
                headlineContent = { Text(tag.name) },
                supportingContent = { 
                    val count = songs.count { it.tags.contains(tag.name) }
                    val stateText = when (tag.state) {
                        TagState.INCLUDED -> "(Included)"
                        TagState.EXCLUDED -> "(Excluded)"
                        TagState.NONE -> ""
                    }
                    Text("$count songs $stateText")
                },
                leadingContent = { 
                    val icon = when (tag.state) {
                        TagState.INCLUDED -> Icons.Default.Check
                        TagState.EXCLUDED -> Icons.Default.Block
                        TagState.NONE -> if (isFavorites) Icons.Default.Favorite else Icons.Default.Tag
                    }
                    val tint = when (tag.state) {
                        TagState.INCLUDED -> MaterialTheme.colorScheme.primary
                        TagState.EXCLUDED -> MaterialTheme.colorScheme.error
                        TagState.NONE -> if (isFavorites) Color.Red else LocalContentColor.current
                    }
                    Icon(icon, contentDescription = null, tint = tint) 
                },
                trailingContent = {
                    Row {
                        IconButton(onClick = { viewModel.cycleTagState(tag.name) }) {
                            Icon(Icons.Default.Refresh, contentDescription = "Cycle State")
                        }
                        if (!isFavorites) {
                            var showMenu by remember { mutableStateOf(false) }
                            Box {
                                IconButton(onClick = { showMenu = true }) {
                                    Icon(Icons.Default.MoreVert, contentDescription = "Options")
                                }
                                DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                                    DropdownMenuItem(
                                        text = { Text("Rename") },
                                        onClick = { 
                                            showMenu = false
                                            tagToEdit = tag 
                                        },
                                        leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null) }
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Delete", color = MaterialTheme.colorScheme.error) },
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
                modifier = Modifier.clickable { viewModel.cycleTagState(tag.name) }
            )
        }
        item {
            var showAddTagDialog by remember { mutableStateOf(false) }

            if (showAddTagDialog) {
                TagEditDialog(
                    title = "Add New Tag",
                    initialName = "",
                    onConfirm = { name ->
                        viewModel.addTag(name)
                        showAddTagDialog = false
                    },
                    onDismiss = { showAddTagDialog = false }
                )
            }

            OutlinedButton(
                onClick = { showAddTagDialog = true },
                modifier = Modifier.fillMaxWidth().padding(top = 16.dp)
            ) {
                Text("Create New Tag")
            }
        }
    }

    // Rename Dialog
    tagToEdit?.let { tag ->
        TagEditDialog(
            title = "Rename Tag",
            initialName = tag.name,
            onConfirm = { newName ->
                viewModel.renameTag(tag.name, newName)
                tagToEdit = null
            },
            onDismiss = { tagToEdit = null }
        )
    }

    // Delete Confirmation Dialog
    showDeleteDialog?.let { tag ->
        AlertDialog(
            onDismissRequest = { showDeleteDialog = null },
            title = { Text("Delete Tag") },
            text = { Text("Are you sure you want to delete the tag \"${tag.name}\"? It will be removed from all songs.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteTag(tag.name)
                        showDeleteDialog = null
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = null }) { Text("Cancel") }
            }
        )
    }
}

@Composable
fun TagEditDialog(
    title: String,
    initialName: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var name by remember { mutableStateOf(initialName) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            TextField(
                value = name,
                onValueChange = { name = it },
                placeholder = { Text("Tag name") },
                singleLine = true
            )
        },
        confirmButton = {
            TextButton(
                onClick = { if (name.isNotBlank()) onConfirm(name) },
                enabled = name.isNotBlank() && name != "Favorites"
            ) { Text("Save") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
