package com.applenotesync.app.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface NotesDao {
    @Query("SELECT * FROM note_list_items ORDER BY modified DESC")
    suspend fun getAllNotes(): List<CachedNoteListItem>

    @Query("SELECT * FROM note_list_items WHERE folderId = :folderId ORDER BY modified DESC")
    suspend fun getNotesByFolder(folderId: Int): List<CachedNoteListItem>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertNotes(notes: List<CachedNoteListItem>)

    @Query("DELETE FROM note_list_items")
    suspend fun clearAllNotes()

    @Query("DELETE FROM note_list_items WHERE folderId = :folderId")
    suspend fun clearNotesByFolder(folderId: Int)

    @Query("SELECT * FROM note_details WHERE id = :noteId")
    suspend fun getNoteDetail(noteId: Int): CachedNoteDetail?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertNoteDetail(detail: CachedNoteDetail)

    @Query("UPDATE note_details SET body = :body WHERE id = :noteId")
    suspend fun updateNoteBody(noteId: Int, body: String)
}
