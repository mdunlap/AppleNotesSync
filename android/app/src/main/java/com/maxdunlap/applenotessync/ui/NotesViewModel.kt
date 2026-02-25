package com.maxdunlap.applenotessync.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.maxdunlap.applenotessync.data.Folder
import com.maxdunlap.applenotessync.data.NoteDetail
import com.maxdunlap.applenotessync.data.NoteListItem
import com.maxdunlap.applenotessync.data.NotesRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

sealed class UiState<out T> {
    data object Loading : UiState<Nothing>()
    data class Success<T>(val data: T) : UiState<T>()
    data class Error(val message: String) : UiState<Nothing>()
}

class NotesViewModel : ViewModel() {
    private val repo = NotesRepository()

    private val _notesState = MutableStateFlow<UiState<List<NoteListItem>>>(UiState.Loading)
    val notesState: StateFlow<UiState<List<NoteListItem>>> = _notesState

    private val _foldersState = MutableStateFlow<UiState<List<Folder>>>(UiState.Loading)
    val foldersState: StateFlow<UiState<List<Folder>>> = _foldersState

    private val _noteDetailState = MutableStateFlow<UiState<NoteDetail>>(UiState.Loading)
    val noteDetailState: StateFlow<UiState<NoteDetail>> = _noteDetailState

    private val _selectedFolder = MutableStateFlow<Int?>(null)
    val selectedFolder: StateFlow<Int?> = _selectedFolder

    fun loadFolders() {
        viewModelScope.launch {
            _foldersState.value = UiState.Loading
            repo.getFolders().fold(
                onSuccess = { _foldersState.value = UiState.Success(it) },
                onFailure = { _foldersState.value = UiState.Error(it.message ?: "Unknown error") },
            )
        }
    }

    fun loadNotes() {
        viewModelScope.launch {
            _notesState.value = UiState.Loading
            repo.getNotes(_selectedFolder.value).fold(
                onSuccess = { _notesState.value = UiState.Success(it) },
                onFailure = { _notesState.value = UiState.Error(it.message ?: "Unknown error") },
            )
        }
    }

    fun loadNoteDetail(noteId: Int) {
        viewModelScope.launch {
            _noteDetailState.value = UiState.Loading
            repo.getNote(noteId).fold(
                onSuccess = { _noteDetailState.value = UiState.Success(it) },
                onFailure = { _noteDetailState.value = UiState.Error(it.message ?: "Unknown error") },
            )
        }
    }

    fun selectFolder(folderId: Int?) {
        _selectedFolder.value = folderId
        loadNotes()
    }
}
