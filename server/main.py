"""
Apple Notes Sync Server
Serves Apple Notes over a REST API for companion apps.
"""

from dataclasses import asdict
from fastapi import FastAPI, HTTPException
from fastapi.middleware.cors import CORSMiddleware

from notes_reader import get_folders, get_notes, get_note

app = FastAPI(title="Apple Notes Sync", version="1.0.0")

app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_methods=["*"],
    allow_headers=["*"],
)


@app.get("/")
def health():
    return {"status": "ok", "service": "apple-notes-sync"}


@app.get("/folders")
def list_folders():
    return get_folders()


@app.get("/notes")
def list_notes(folder_id: int | None = None):
    """List all notes. Optionally filter by folder_id."""
    notes = get_notes(folder_id)
    # Return without body for listing (lighter payload)
    return [
        {
            "id": n.id,
            "title": n.title,
            "snippet": n.snippet,
            "folder": n.folder,
            "created": n.created,
            "modified": n.modified,
            "is_pinned": n.is_pinned,
            "has_checklist": n.has_checklist,
        }
        for n in notes
    ]


@app.get("/notes/{note_id}")
def read_note(note_id: int):
    """Get a single note with full body content."""
    note = get_note(note_id)
    if note is None:
        raise HTTPException(status_code=404, detail="Note not found")
    return asdict(note)
