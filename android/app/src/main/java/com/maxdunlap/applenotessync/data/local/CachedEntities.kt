package com.maxdunlap.applenotessync.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.maxdunlap.applenotessync.data.NoteDetail
import com.maxdunlap.applenotessync.data.NoteListItem

@Entity(tableName = "note_list_items")
data class CachedNoteListItem(
    @PrimaryKey val id: Int,
    val title: String,
    val snippet: String,
    val folder: String,
    val folderId: Int?,
    val created: String,
    val modified: String,
    val is_pinned: Boolean,
    val has_checklist: Boolean,
)

@Entity(tableName = "note_details")
data class CachedNoteDetail(
    @PrimaryKey val id: Int,
    val title: String,
    val snippet: String,
    val body: String,
    val folder: String,
    val created: String,
    val modified: String,
    val is_pinned: Boolean,
    val has_checklist: Boolean,
)

fun NoteListItem.toCached(folderId: Int?) = CachedNoteListItem(
    id = id, title = title, snippet = snippet, folder = folder,
    folderId = folderId, created = created, modified = modified,
    is_pinned = is_pinned, has_checklist = has_checklist,
)

fun CachedNoteListItem.toApi() = NoteListItem(
    id = id, title = title, snippet = snippet, folder = folder,
    created = created, modified = modified,
    is_pinned = is_pinned, has_checklist = has_checklist,
)

fun NoteDetail.toCached() = CachedNoteDetail(
    id = id, title = title, snippet = snippet, body = body,
    folder = folder, created = created, modified = modified,
    is_pinned = is_pinned, has_checklist = has_checklist,
)

fun CachedNoteDetail.toApi() = NoteDetail(
    id = id, title = title, snippet = snippet, body = body,
    folder = folder, created = created, modified = modified,
    is_pinned = is_pinned, has_checklist = has_checklist,
)
