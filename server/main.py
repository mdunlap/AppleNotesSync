"""
Apple Notes Sync Server
Serves Apple Notes over a REST API for companion apps.
"""

import asyncio
import socket
from contextlib import asynccontextmanager
from dataclasses import asdict

from fastapi import FastAPI, HTTPException
from fastapi.middleware.cors import CORSMiddleware
from pydantic import BaseModel
from zeroconf import ServiceInfo, Zeroconf

from notes_reader import get_folders, get_notes, get_note
from notes_writer import create_note, edit_note, delete_note


# --- mDNS setup ---

SERVICE_TYPE = "_applenotesync._tcp.local."
SERVICE_NAME = "AppleNotesSync._applenotesync._tcp.local."
SERVICE_PORT = 8642

_zeroconf: Zeroconf | None = None
_service_info: ServiceInfo | None = None


def _get_local_ip() -> str:
    """Get the local IP address by opening a UDP socket (no data is sent)."""
    s = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
    try:
        s.connect(("8.8.8.8", 80))
        return s.getsockname()[0]
    finally:
        s.close()


def _register_mdns():
    """Register the mDNS service (runs in a thread to avoid blocking the event loop)."""
    global _zeroconf, _service_info
    local_ip = _get_local_ip()
    _service_info = ServiceInfo(
        SERVICE_TYPE,
        SERVICE_NAME,
        addresses=[socket.inet_aton(local_ip)],
        port=SERVICE_PORT,
        properties={"path": "/", "version": "1.0"},
    )
    _zeroconf = Zeroconf()
    _zeroconf.register_service(_service_info)


def _unregister_mdns():
    """Unregister the mDNS service."""
    global _zeroconf, _service_info
    if _zeroconf and _service_info:
        _zeroconf.unregister_service(_service_info)
        _zeroconf.close()
        _zeroconf = None
        _service_info = None


@asynccontextmanager
async def lifespan(app: FastAPI):
    """Startup and shutdown events."""
    await asyncio.to_thread(_register_mdns)
    yield
    await asyncio.to_thread(_unregister_mdns)


# --- FastAPI app ---

app = FastAPI(title="Apple Notes Sync", version="1.0.0", lifespan=lifespan)

app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)


# --- Request models ---

class CreateNoteRequest(BaseModel):
    title: str
    body: str
    folder: str = "Notes"


class EditNoteRequest(BaseModel):
    body: str


# --- Read endpoints ---

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


# --- Write endpoints ---

@app.post("/notes", status_code=201)
async def create_note_endpoint(req: CreateNoteRequest):
    """Create a new note."""
    try:
        result = await create_note(req.title, req.body, req.folder)
        return result
    except RuntimeError as e:
        raise HTTPException(status_code=500, detail=str(e))


@app.put("/notes/{note_id}")
async def edit_note_endpoint(note_id: int, req: EditNoteRequest):
    """Edit a note's body content."""
    # Verify the note exists first
    note = get_note(note_id)
    if note is None:
        raise HTTPException(status_code=404, detail="Note not found")
    try:
        await edit_note(note_id, req.body)
        return {"status": "ok"}
    except RuntimeError as e:
        raise HTTPException(status_code=500, detail=str(e))


@app.delete("/notes/{note_id}")
async def delete_note_endpoint(note_id: int):
    """Delete a note (moves to Recently Deleted)."""
    note = get_note(note_id)
    if note is None:
        raise HTTPException(status_code=404, detail="Note not found")
    try:
        await delete_note(note_id)
        return {"status": "ok"}
    except RuntimeError as e:
        raise HTTPException(status_code=500, detail=str(e))
