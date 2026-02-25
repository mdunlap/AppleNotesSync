"""
Apple Notes SQLite database reader.
Reads notes from the local macOS Apple Notes database.
"""

import gzip
import os
import sqlite3
import zlib
from dataclasses import dataclass
from datetime import datetime, timezone


# Apple's Core Data epoch: 2001-01-01 00:00:00 UTC
APPLE_EPOCH = datetime(2001, 1, 1, tzinfo=timezone.utc)

DB_PATH = os.path.expanduser(
    "~/Library/Group Containers/group.com.apple.notes/NoteStore.sqlite"
)


@dataclass
class Note:
    id: int
    title: str
    snippet: str
    body: str
    folder: str
    created: str
    modified: str
    is_pinned: bool
    has_checklist: bool


@dataclass
class Folder:
    id: int
    name: str
    note_count: int


def _apple_timestamp_to_iso(ts) -> str | None:
    """Convert Apple Core Data timestamp to ISO 8601 string."""
    if ts is None:
        return None
    from datetime import timedelta
    dt = APPLE_EPOCH + timedelta(seconds=ts)
    return dt.isoformat()


def _read_varint(data: bytes, pos: int) -> tuple[int, int]:
    """Read a protobuf varint, return (value, new_pos)."""
    result = 0
    shift = 0
    while pos < len(data):
        b = data[pos]
        result |= (b & 0x7F) << shift
        pos += 1
        if not (b & 0x80):
            break
        shift += 7
    return result, pos


def _parse_protobuf(data: bytes) -> dict:
    """Minimally parse protobuf wire format into {field_number: [values]}."""
    fields: dict[int, list] = {}
    pos = 0
    while pos < len(data):
        try:
            tag, pos = _read_varint(data, pos)
        except Exception:
            break
        field_number = tag >> 3
        wire_type = tag & 0x07

        if wire_type == 0:  # varint
            _, pos = _read_varint(data, pos)
        elif wire_type == 2:  # length-delimited (string, bytes, sub-message)
            length, pos = _read_varint(data, pos)
            if pos + length > len(data):
                break
            value = data[pos:pos + length]
            fields.setdefault(field_number, []).append(value)
            pos += length
        elif wire_type == 5:  # 32-bit
            pos += 4
        elif wire_type == 1:  # 64-bit
            pos += 8
        else:
            break
    return fields


def _extract_text_from_protobuf(data: bytes) -> str:
    """Extract note text from Apple Notes gzipped protobuf data.

    Apple Notes uses a CRDT-based protobuf format (Mergeable). The note text
    is typically stored via root -> field 2 -> field 3 -> field 2 path.
    If the structured parse returns empty, falls back to extracting printable
    text strings from the raw decompressed data.
    """
    try:
        decompressed = gzip.decompress(data)
    except Exception:
        try:
            decompressed = zlib.decompress(data)
        except Exception:
            return ""

    text = _try_structured_parse(decompressed)
    if not text:
        text = _fallback_extract_text(decompressed)

    # Clean up the extracted text
    text = text.replace("\u2028", "\n")   # Line Separator -> newline
    text = text.replace("\ufffc", "")     # Object Replacement Character -> remove
    text = text.strip()                   # Strip leading/trailing whitespace

    return text


def _try_structured_parse(decompressed: bytes) -> str:
    """Try structured protobuf parse: root -> field 2 -> field 3 -> field 2."""
    root = _parse_protobuf(decompressed)
    if 2 not in root:
        return ""

    note_store = _parse_protobuf(root[2][0])
    # Field 3 contains the attributed string with paragraphs
    if 3 not in note_store:
        return ""

    # Each entry in field 3 is a paragraph/text run sub-message
    text_parts = []
    for para_data in note_store[3]:
        para = _parse_protobuf(para_data)
        # Field 2 contains the text string for this run
        if 2 in para:
            for text_bytes in para[2]:
                try:
                    text_parts.append(text_bytes.decode("utf-8"))
                except UnicodeDecodeError:
                    pass

    return "\n".join(text_parts) if text_parts else ""


def _fallback_extract_text(decompressed: bytes) -> str:
    """Fallback: extract printable text strings from raw decompressed data.

    Scans for sequences of printable UTF-8 characters (minimum 4 chars)
    and joins them together. Used when structured protobuf parsing fails.
    """
    import re
    # Try decoding as UTF-8, replacing errors
    raw_text = decompressed.decode("utf-8", errors="replace")
    # Match runs of printable characters (letters, digits, punctuation, spaces)
    # but not control characters (except newline/tab)
    parts = re.findall(r'[\w\s.,;:!?\'\"()\-\u00C0-\uFFFF]{4,}', raw_text)
    if not parts:
        return ""
    return " ".join(parts).strip()


def _get_connection() -> sqlite3.Connection:
    """Get a read-only connection to the Notes database."""
    conn = sqlite3.connect(f"file:{DB_PATH}?mode=ro", uri=True)
    conn.row_factory = sqlite3.Row
    return conn


def get_folders() -> list[Folder]:
    """Get all non-deleted folders."""
    conn = _get_connection()
    try:
        cur = conn.execute("""
            SELECT
                f.Z_PK as id,
                f.ZTITLE2 as name,
                COUNT(n.Z_PK) as note_count
            FROM ZICCLOUDSYNCINGOBJECT f
            LEFT JOIN ZICCLOUDSYNCINGOBJECT n
                ON n.ZFOLDER = f.Z_PK
                AND n.ZTITLE1 IS NOT NULL
                AND (n.ZMARKEDFORDELETION IS NULL OR n.ZMARKEDFORDELETION = 0)
            WHERE f.ZTITLE2 IS NOT NULL
              AND (f.ZMARKEDFORDELETION IS NULL OR f.ZMARKEDFORDELETION = 0)
            GROUP BY f.Z_PK
            ORDER BY f.ZTITLE2
        """)
        return [Folder(id=r["id"], name=r["name"], note_count=r["note_count"]) for r in cur]
    finally:
        conn.close()


def get_notes(folder_id: int | None = None) -> list[Note]:
    """Get all non-deleted notes, optionally filtered by folder."""
    conn = _get_connection()
    try:
        query = """
            SELECT
                n.Z_PK as id,
                n.ZTITLE1 as title,
                n.ZSNIPPET as snippet,
                n.ZCREATIONDATE3 as created,
                n.ZMODIFICATIONDATE1 as modified,
                n.ZFOLDER as folder_id,
                n.ZISPINNED as is_pinned,
                n.ZHASCHECKLIST as has_checklist,
                f.ZTITLE2 as folder_name,
                n.ZNOTEDATA as notedata_id
            FROM ZICCLOUDSYNCINGOBJECT n
            LEFT JOIN ZICCLOUDSYNCINGOBJECT f ON n.ZFOLDER = f.Z_PK
            WHERE n.ZTITLE1 IS NOT NULL
              AND (n.ZMARKEDFORDELETION IS NULL OR n.ZMARKEDFORDELETION = 0)
        """
        params = []
        if folder_id is not None:
            query += " AND n.ZFOLDER = ?"
            params.append(folder_id)

        query += " ORDER BY n.ZISPINNED DESC, n.ZMODIFICATIONDATE1 DESC"

        cur = conn.execute(query, params)
        notes = []
        for r in cur:
            notes.append(Note(
                id=r["id"],
                title=r["title"] or "",
                snippet=r["snippet"] or "",
                body="",  # loaded on demand via get_note()
                folder=r["folder_name"] or "Notes",
                created=_apple_timestamp_to_iso(r["created"]) or "",
                modified=_apple_timestamp_to_iso(r["modified"]) or "",
                is_pinned=bool(r["is_pinned"]),
                has_checklist=bool(r["has_checklist"]),
            ))
        return notes
    finally:
        conn.close()


def get_note(note_id: int) -> Note | None:
    """Get a single note with its full body content."""
    conn = _get_connection()
    try:
        cur = conn.execute("""
            SELECT
                n.Z_PK as id,
                n.ZTITLE1 as title,
                n.ZSNIPPET as snippet,
                n.ZCREATIONDATE3 as created,
                n.ZMODIFICATIONDATE1 as modified,
                n.ZISPINNED as is_pinned,
                n.ZHASCHECKLIST as has_checklist,
                f.ZTITLE2 as folder_name,
                nd.ZDATA as body_data
            FROM ZICCLOUDSYNCINGOBJECT n
            LEFT JOIN ZICCLOUDSYNCINGOBJECT f ON n.ZFOLDER = f.Z_PK
            LEFT JOIN ZICNOTEDATA nd ON nd.ZNOTE = n.Z_PK
            WHERE n.Z_PK = ?
              AND (n.ZMARKEDFORDELETION IS NULL OR n.ZMARKEDFORDELETION = 0)
        """, (note_id,))
        r = cur.fetchone()
        if r is None:
            return None

        body = ""
        if r["body_data"]:
            body = _extract_text_from_protobuf(r["body_data"])

        return Note(
            id=r["id"],
            title=r["title"] or "",
            snippet=r["snippet"] or "",
            body=body,
            folder=r["folder_name"] or "Notes",
            created=_apple_timestamp_to_iso(r["created"]) or "",
            modified=_apple_timestamp_to_iso(r["modified"]) or "",
            is_pinned=bool(r["is_pinned"]),
            has_checklist=bool(r["has_checklist"]),
        )
    finally:
        conn.close()
