package com.applenotesync.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NoteDetailScreen(
    noteId: Int,
    viewModel: NotesViewModel = viewModel(),
    onBack: () -> Unit,
) {
    val detailState by viewModel.noteDetailState.collectAsState()
    val syncStatus by viewModel.syncStatus.collectAsState()
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    var editedBody by remember { mutableStateOf("") }
    var bodySeeded by remember { mutableStateOf(false) }

    LaunchedEffect(noteId) {
        viewModel.loadNoteDetail(noteId)
    }

    // Seed editor when note first loads
    LaunchedEffect(detailState) {
        if (detailState is UiState.Success && !bodySeeded) {
            editedBody = (detailState as UiState.Success).data.body
            bodySeeded = true
        }
    }

    val titleText = when (val state = detailState) {
        is UiState.Success -> state.data.title
        else -> "Note"
    }

    val statusText = when (syncStatus) {
        SyncStatus.Saving -> "Saving..."
        SyncStatus.Saved -> "Saved"
        SyncStatus.Error -> "Save failed"
        SyncStatus.Idle -> null
    }

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            LargeTopAppBar(
                title = { Text(titleText) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (statusText != null) {
                        Text(
                            text = statusText,
                            style = MaterialTheme.typography.labelMedium,
                            color = if (syncStatus == SyncStatus.Error)
                                MaterialTheme.colorScheme.error
                            else
                                MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(end = 16.dp),
                        )
                    }
                },
                scrollBehavior = scrollBehavior,
            )
        }
    ) { padding ->
        when (val state = detailState) {
            is UiState.Loading -> {
                Box(
                    modifier = Modifier.fillMaxSize().padding(padding),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator()
                }
            }
            is UiState.Error -> {
                Box(
                    modifier = Modifier.fillMaxSize().padding(padding),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Failed to load note")
                        Text(state.message, style = MaterialTheme.typography.bodySmall)
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(onClick = { viewModel.loadNoteDetail(noteId) }) {
                            Text("Retry")
                        }
                    }
                }
            }
            is UiState.Success -> {
                val note = state.data
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .verticalScroll(rememberScrollState())
                        .padding(16.dp)
                ) {
                    Row {
                        Text(
                            text = note.folder,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.tertiary,
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = formatDetailDate(note.modified),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))
                    BasicTextField(
                        value = editedBody,
                        onValueChange = {
                            editedBody = it
                            viewModel.debouncedSave(noteId, it)
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .defaultMinSize(minHeight = 300.dp),
                        textStyle = MaterialTheme.typography.bodyLarge.copy(
                            lineHeight = 28.sp,
                            color = MaterialTheme.colorScheme.onSurface,
                        ),
                        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                    )
                }
            }
        }
    }
}

private fun formatDetailDate(isoDate: String): String {
    return try {
        val dt = OffsetDateTime.parse(isoDate)
        dt.format(DateTimeFormatter.ofPattern("MMM d, yyyy 'at' h:mm a"))
    } catch (_: Exception) {
        ""
    }
}
