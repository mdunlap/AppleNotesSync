package com.maxdunlap.applenotessync.data

class NotesRepository(private val api: NotesApi = NotesApi()) {

    suspend fun getFolders(): Result<List<Folder>> = runCatching {
        api.getFolders()
    }

    suspend fun getNotes(folderId: Int? = null): Result<List<NoteListItem>> = runCatching {
        api.getNotes(folderId)
    }

    suspend fun getNote(noteId: Int): Result<NoteDetail> = runCatching {
        api.getNote(noteId)
    }
}
