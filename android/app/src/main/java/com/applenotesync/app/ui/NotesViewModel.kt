package com.applenotesync.app.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.applenotesync.app.data.Folder
import com.applenotesync.app.data.NoteDetail
import com.applenotesync.app.data.NoteListItem
import com.applenotesync.app.data.NotesRepository
import com.applenotesync.app.data.ServerDiscovery
import com.applenotesync.app.data.ServerState
import com.applenotesync.app.data.local.NotesDatabase
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
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

enum class SyncStatus { Idle, Saving, Saved, Error }

class NotesViewModel(application: Application) : AndroidViewModel(application) {
    val serverDiscovery = ServerDiscovery(application)
    private val dao = NotesDatabase.getInstance(application).notesDao()

    private val repo: NotesRepository
        get() = NotesRepository(getEffectiveServerUrl(), dao)

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
        _notesState, _searchQuery, _selectedFolder
    ) { state, query, folder ->
        if (state !is UiState.Success) state
        else {
            var notes = state.data
            // Hide "Recently Deleted" notes from the All tab
            if (folder == null) {
                notes = notes.filter { !it.folder.equals("Recently Deleted", ignoreCase = true) }
            }
            if (query.isNotBlank()) {
                notes = notes.filter {
                    it.title.contains(query, ignoreCase = true) ||
                        it.snippet.contains(query, ignoreCase = true)
                }
            }
            UiState.Success(notes)
        }
    }.stateIn(viewModelScope, SharingStarted.Lazily, UiState.Loading)

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing

    // Auto-save state
    private val _syncStatus = MutableStateFlow(SyncStatus.Idle)
    val syncStatus: StateFlow<SyncStatus> = _syncStatus

    private var autoSaveJob: Job? = null

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

    fun debouncedSave(noteId: Int, body: String) {
        autoSaveJob?.cancel()
        _syncStatus.value = SyncStatus.Idle
        autoSaveJob = viewModelScope.launch {
            delay(1500) // 1.5s debounce
            _syncStatus.value = SyncStatus.Saving
            repo.editNote(noteId, body).fold(
                onSuccess = {
                    _syncStatus.value = SyncStatus.Saved
                    delay(2000)
                    if (_syncStatus.value == SyncStatus.Saved) {
                        _syncStatus.value = SyncStatus.Idle
                    }
                },
                onFailure = {
                    _syncStatus.value = SyncStatus.Error
                },
            )
        }
    }

    fun selectFolder(folderId: Int?) {
        _selectedFolder.value = folderId
        loadNotes()
    }

    private fun getEffectiveServerUrl(): String {
        val prefs = getApplication<android.app.Application>()
            .getSharedPreferences("apple_notes_sync", android.content.Context.MODE_PRIVATE)
        val manual = prefs.getString("server_url", null)?.trim()
        if (!manual.isNullOrBlank()) {
            return if (manual.startsWith("http://") || manual.startsWith("https://")) manual
                   else "http://$manual"
        }
        val discoveryState = serverDiscovery.state.value
        if (discoveryState is ServerState.Found) {
            return "http://${discoveryState.host}:${discoveryState.port}"
        }
        return com.applenotesync.app.BuildConfig.SERVER_URL
    }

    override fun onCleared() {
        super.onCleared()
        serverDiscovery.stopDiscovery()
    }
}
