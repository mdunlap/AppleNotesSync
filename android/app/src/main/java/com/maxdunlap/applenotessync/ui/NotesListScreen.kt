package com.maxdunlap.applenotessync.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.maxdunlap.applenotessync.data.NoteListItem
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotesListScreen(
    viewModel: NotesViewModel = viewModel(),
    onNoteClick: (Int) -> Unit,
) {
    val uiState by viewModel.notesState.collectAsState()
    val folders by viewModel.foldersState.collectAsState()
    val selectedFolder by viewModel.selectedFolder.collectAsState()

    LaunchedEffect(Unit) {
        viewModel.loadFolders()
        viewModel.loadNotes()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Apple Notes") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                ),
            )
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding)) {
            // Folder filter chips
            if (folders is UiState.Success) {
                val folderList = (folders as UiState.Success).data
                ScrollableTabRow(
                    selectedTabIndex = folderList.indexOfFirst { it.id == selectedFolder }.let { if (it < 0) 0 else it + 1 },
                    modifier = Modifier.fillMaxWidth(),
                    edgePadding = 8.dp,
                ) {
                    Tab(
                        selected = selectedFolder == null,
                        onClick = { viewModel.selectFolder(null) },
                        text = { Text("All") },
                    )
                    folderList.filter { it.note_count > 0 }.forEach { folder ->
                        Tab(
                            selected = selectedFolder == folder.id,
                            onClick = { viewModel.selectFolder(folder.id) },
                            text = { Text("${folder.name} (${folder.note_count})") },
                        )
                    }
                }
            }

            when (val state = uiState) {
                is UiState.Loading -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
                is UiState.Error -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("Failed to load notes", style = MaterialTheme.typography.bodyLarge)
                            Text(state.message, style = MaterialTheme.typography.bodySmall)
                            Spacer(modifier = Modifier.height(16.dp))
                            Button(onClick = { viewModel.loadNotes() }) {
                                Text("Retry")
                            }
                        }
                    }
                }
                is UiState.Success -> {
                    LazyColumn(modifier = Modifier.fillMaxSize()) {
                        items(state.data, key = { it.id }) { note ->
                            NoteRow(note = note, onClick = { onNoteClick(note.id) })
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun NoteRow(note: NoteListItem, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp)
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = if (note.is_pinned)
                MaterialTheme.colorScheme.secondaryContainer
            else
                MaterialTheme.colorScheme.surface,
        ),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (note.is_pinned) {
                    Text("📌 ", style = MaterialTheme.typography.bodySmall)
                }
                Text(
                    text = note.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = formatDate(note.modified),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (note.snippet.isNotBlank()) {
                Text(
                    text = note.snippet,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
            Text(
                text = note.folder,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.tertiary,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
    }
}

private fun formatDate(isoDate: String): String {
    return try {
        val dt = OffsetDateTime.parse(isoDate)
        dt.format(DateTimeFormatter.ofPattern("MMM d"))
    } catch (_: Exception) {
        ""
    }
}
