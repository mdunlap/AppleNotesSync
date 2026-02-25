package com.maxdunlap.applenotessync.data

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.android.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.client.request.setBody
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class NoteListItem(
    val id: Int,
    val title: String,
    val snippet: String,
    val folder: String,
    val created: String,
    val modified: String,
    val is_pinned: Boolean,
    val has_checklist: Boolean,
)

@Serializable
data class NoteDetail(
    val id: Int,
    val title: String,
    val snippet: String,
    val body: String,
    val folder: String,
    val created: String,
    val modified: String,
    val is_pinned: Boolean,
    val has_checklist: Boolean,
)

@Serializable
data class Folder(
    val id: Int,
    val name: String,
    val note_count: Int,
)

class NotesApi(private val baseUrl: String) {
    private val client = HttpClient(Android) {
        install(ContentNegotiation) {
            json(Json { ignoreUnknownKeys = true })
        }
    }

    suspend fun getFolders(): List<Folder> =
        client.get("$baseUrl/folders").body()

    suspend fun getNotes(folderId: Int? = null): List<NoteListItem> {
        val url = if (folderId != null) "$baseUrl/notes?folder_id=$folderId" else "$baseUrl/notes"
        return client.get(url).body()
    }

    suspend fun getNote(noteId: Int): NoteDetail =
        client.get("$baseUrl/notes/$noteId").body()

    suspend fun editNote(noteId: Int, body: String) {
        client.put("$baseUrl/notes/$noteId") {
            contentType(ContentType.Application.Json)
            setBody(EditNoteRequest(body))
        }
    }
}

@Serializable
data class EditNoteRequest(val body: String)
