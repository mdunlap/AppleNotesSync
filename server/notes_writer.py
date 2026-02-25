"""
Apple Notes writer using JXA (JavaScript for Automation).
Creates, edits, and deletes notes via osascript subprocess calls.
"""

import asyncio
import json
import os
import sqlite3
import subprocess

DB_PATH = os.path.expanduser(
    "~/Library/Group Containers/group.com.apple.notes/NoteStore.sqlite"
)

# Lock to prevent concurrent osascript calls
_osascript_lock = asyncio.Lock()

# Cached store UUID
_store_uuid: str | None = None


def get_store_uuid() -> str:
    """Query the store UUID from Z_METADATA table. Cached after first call."""
    global _store_uuid
    if _store_uuid is not None:
        return _store_uuid

    conn = sqlite3.connect(f"file:{DB_PATH}?mode=ro", uri=True)
    try:
        cur = conn.execute("SELECT Z_UUID FROM Z_METADATA")
        row = cur.fetchone()
        if row is None:
            raise RuntimeError("Could not read Z_UUID from Z_METADATA")
        _store_uuid = row[0]
        return _store_uuid
    finally:
        conn.close()


def _note_id_from_pk(pk: int) -> str:
    """Convert a SQLite Z_PK to an AppleScript-compatible note ID.

    The mapping is: x-coredata://{STORE_UUID}/ICNote/p{Z_PK}
    """
    uuid = get_store_uuid()
    return f"x-coredata://{uuid}/ICNote/p{pk}"


async def _run_jxa(script: str) -> str:
    """Run a JXA script via osascript and return stdout.

    Uses an asyncio lock to prevent concurrent osascript calls.
    """
    async with _osascript_lock:
        proc = await asyncio.create_subprocess_exec(
            "osascript", "-l", "JavaScript", "-e", script,
            stdout=subprocess.PIPE,
            stderr=subprocess.PIPE,
        )
        stdout, stderr = await proc.communicate()

    if proc.returncode != 0:
        error_msg = stderr.decode("utf-8", errors="replace").strip()
        raise RuntimeError(f"osascript failed (exit {proc.returncode}): {error_msg}")

    return stdout.decode("utf-8", errors="replace").strip()


async def create_note(title: str, body: str, folder: str = "Notes") -> dict:
    """Create a new note in Apple Notes.

    Args:
        title: The note title.
        body: The note body in plain text. Newlines become <div> paragraphs.
        folder: The folder name to create the note in (default: "Notes").

    Returns:
        dict with 'id' (int-ish from name) and 'name' of the created note.
    """
    # Convert body text to HTML with <div> paragraphs
    body_html = "".join(f"<div>{_escape_html(line) or '<br>'}</div>" for line in body.split("\n"))
    title_html = _escape_html(title)

    # Full HTML body includes title as first line
    full_html = f"<div><b>{title_html}</b></div>{body_html}"

    # Escape for JS string embedding
    full_html_js = _escape_js_string(full_html)
    folder_js = _escape_js_string(folder)

    script = f"""
        var app = Application("Notes");
        var folder;
        var folders = app.folders.whose({{name: "{folder_js}"}});
        if (folders.length > 0) {{
            folder = folders[0];
        }} else {{
            folder = app.defaultAccount().folders.whose({{name: "Notes"}})[0];
        }}
        var note = app.Note({{body: "{full_html_js}"}});
        folder.notes.push(note);
        JSON.stringify({{id: note.id(), name: note.name()}});
    """

    result = await _run_jxa(script)
    return json.loads(result)


async def edit_note(note_id: int, body: str) -> None:
    """Edit an existing note's body content.

    WARNING: Editing a note that has attachments (images, files, etc.) will
    cause the attachments to be lost. The note body is fully replaced.

    Args:
        note_id: The SQLite Z_PK of the note.
        body: The new body content in plain text. Newlines become <div> paragraphs.
    """
    coredata_id = _note_id_from_pk(note_id)
    coredata_id_js = _escape_js_string(coredata_id)

    # Convert body text to HTML
    body_html = "".join(f"<div>{_escape_html(line) or '<br>'}</div>" for line in body.split("\n"))
    body_html_js = _escape_js_string(body_html)

    script = f"""
        var app = Application("Notes");
        var note = app.notes.byId("{coredata_id_js}");
        note.body = "{body_html_js}";
        "ok";
    """

    await _run_jxa(script)


async def delete_note(note_id: int) -> None:
    """Delete a note (moves it to Recently Deleted).

    Args:
        note_id: The SQLite Z_PK of the note.
    """
    coredata_id = _note_id_from_pk(note_id)
    coredata_id_js = _escape_js_string(coredata_id)

    script = f"""
        var app = Application("Notes");
        var note = app.notes.byId("{coredata_id_js}");
        app.delete(note);
        "ok";
    """

    await _run_jxa(script)


def _escape_html(text: str) -> str:
    """Escape HTML special characters."""
    return (
        text.replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace('"', "&quot;")
    )


def _escape_js_string(text: str) -> str:
    """Escape a string for safe embedding in a JS double-quoted string literal."""
    return (
        text.replace("\\", "\\\\")
            .replace('"', '\\"')
            .replace("\n", "\\n")
            .replace("\r", "\\r")
            .replace("\t", "\\t")
    )
