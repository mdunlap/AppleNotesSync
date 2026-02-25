package com.maxdunlap.applenotessync.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.maxdunlap.applenotessync.data.Folder
import com.maxdunlap.applenotessync.data.NoteDetail
import com.maxdunlap.applenotessync.data.NoteListItem
import com.maxdunlap.applenotessync.data.NotesRepository
import com.maxdunlap.applenotessync.data.ServerDiscovery
import com.maxdunlap.applenotessync.data.ServerState
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed class UiState<out T> {
    data object Loading : UiState<Nothing>()
    data class Success<T>(val data: T) : UiState<T>()
    data class Error(val message: String) : UiState<Nothing>()
}

class NotesViewModel(application: Application) : AndroidViewModel(application) {
    val serverDiscovery = ServerDiscovery(application)

    private val repo: NotesRepository
        get() = NotesRepository(getEffectiveServerUrl())

    private val _notesState = MutableStateFlow<UiState<List<NoteListItem>>>(UiState.Loading)
    val notesState: StateFlow<UiState<List<NoteListItem>>> = _notesState

    private val _foldersState = MutableStateFlow<UiState<List<Folder>>>(UiState.Loading)
    val foldersState: StateFlow<UiState<List<Folder>>> = _foldersState

    private val _noteDetailState = MutableStateFlow<UiState<NoteDetail>>(UiState.Loading)
    val noteDetailState: StateFlow<UiState<NoteDetail>> = _noteDetailState

    private val _selectedFolder = MutableStateFlow<Int?>(null)
    val selectedFolder: StateFlow<Int?> = _selectedFolder

    private val _searchQuery = MutableStateFlow("")

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    val filteredNotes: StateFlow<UiState<List<NoteListItem>>> = combine(
        _notesState, _searchQuery
    ) { state, query ->
        if (query.isBlank() || state !is UiState.Success) state
        else UiState.Success(state.data.filter {
            it.title.contains(query, ignoreCase = true) ||
                it.snippet.contains(query, ignoreCase = true)
        })
    }.stateIn(viewModelScope, SharingStarted.Lazily, UiState.Loading)

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing

    init {
        serverDiscovery.startDiscovery()
    }

    fun refresh() {
        viewModelScope.launch {
            _isRefreshing.value = true
            val foldersJob = async { repo.getFolders() }
            val notesJob = async { repo.getNotes(_selectedFolder.value) }
            foldersJob.await().fold(
                onSuccess = { _foldersState.value = UiState.Success(it) },
                onFailure = { _foldersState.value = UiState.Error(it.message ?: "Unknown error") },
            )
            notesJob.await().fold(
                onSuccess = { _notesState.value = UiState.Success(it) },
                onFailure = { _notesState.value = UiState.Error(it.message ?: "Unknown error") },
            )
            _isRefreshing.value = false
        }
    }

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

    private fun getEffectiveServerUrl(): String {
        val saved = getServerUrl(getApplication())
        if (saved.isNotBlank() && saved != com.maxdunlap.applenotessync.BuildConfig.SERVER_URL) {
            return saved
        }
        val discoveryState = serverDiscovery.state.value
        if (discoveryState is ServerState.Found) {
            return "http://${discoveryState.host}:${discoveryState.port}"
        }
        return saved
    }

    override fun onCleared() {
        super.onCleared()
        serverDiscovery.stopDiscovery()
    }
}
