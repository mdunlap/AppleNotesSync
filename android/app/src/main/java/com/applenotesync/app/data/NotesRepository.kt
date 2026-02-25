package com.applenotesync.app.data

import com.applenotesync.app.data.local.NotesDao
import com.applenotesync.app.data.local.toApi
import com.applenotesync.app.data.local.toCached

class NotesRepository(baseUrl: String, private val dao: NotesDao) {
    private val api = NotesApi(baseUrl)

    suspend fun getFolders(): Result<List<Folder>> = runCatching {
        api.getFolders()
    }

    suspend fun getNotes(folderId: Int? = null): Result<List<NoteListItem>> {
        // Try cache first
        val cached = if (folderId != null) dao.getNotesByFolder(folderId) else dao.getAllNotes()
        val cacheResult = cached.map { it.toApi() }

        // Fetch from network in background, update cache
        val networkResult = runCatching { api.getNotes(folderId) }
        if (networkResult.isSuccess) {
            val notes = networkResult.getOrThrow()
            if (folderId != null) dao.clearNotesByFolder(folderId) else dao.clearAllNotes()
            dao.insertNotes(notes.map { it.toCached(folderId) })
            return Result.success(notes)
        }

        // Network failed — return cache if available
        if (cacheResult.isNotEmpty()) {
            return Result.success(cacheResult)
        }
        return networkResult
    }

    suspend fun getNote(noteId: Int): Result<NoteDetail> {
        // Try cache first
        val cached = dao.getNoteDetail(noteId)

        val networkResult = runCatching { api.getNote(noteId) }
        if (networkResult.isSuccess) {
            val detail = networkResult.getOrThrow()
            dao.insertNoteDetail(detail.toCached())
            return Result.success(detail)
        }

        // Network failed — return cache if available
        if (cached != null) {
            return Result.success(cached.toApi())
        }
        return networkResult
    }

    suspend fun editNote(noteId: Int, body: String): Result<Unit> {
        // Always update local cache immediately
        dao.updateNoteBody(noteId, body)
        // Then push to server
        return runCatching { api.editNote(noteId, body) }
    }
}
