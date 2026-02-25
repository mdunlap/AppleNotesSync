# Apple Notes Sync

Read and edit your Apple Notes from an Android device over your local network.

This project has two parts: a Python server that runs on your Mac and reads/writes Apple Notes, and a Jetpack Compose Android app that connects to it.

## How it works

Apple Notes stores everything in a local SQLite database. The server reads directly from that database, parses the protobuf-encoded note bodies, and serves them over a REST API. Editing goes the other way — the server uses JXA (JavaScript for Automation) to write back to Apple Notes through AppleScript, so your changes sync normally through iCloud.

The Android app discovers the server automatically via mDNS on your local network. You can also set the server IP manually in settings, which is useful if you're connecting over a VPN like WireGuard.

Notes are cached locally on the device with Room so you can still browse them offline. Editing auto-saves with a 1.5 second debounce as you type.

## Setup

### Server (macOS)

```bash
cd server
python3 -m venv ../.venv
source ../.venv/bin/activate
pip install -r requirements.txt
uvicorn main:app --host 0.0.0.0 --port 8642
```

The server needs Full Disk Access in System Settings > Privacy & Security to read the Notes database.

### Android

Build with Android Studio or from the command line:

```bash
cd android
./gradlew assembleDebug
adb install app/build/outputs/apk/debug/app-debug.apk
```

On first launch, go to Settings (gear icon) and enter your Mac's IP if auto-discovery doesn't find it.

## API

| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/` | Health check |
| GET | `/folders` | List all folders |
| GET | `/notes` | List all notes (optional `?folder_id=`) |
| GET | `/notes/{id}` | Get note with full body |
| POST | `/notes` | Create a note |
| PUT | `/notes/{id}` | Edit a note's body |
| DELETE | `/notes/{id}` | Delete a note |

## Requirements

- macOS with Apple Notes
- Python 3.10+
- Android 8.0+ (API 26)
- Both devices on the same network (or connected via VPN)

## License

MIT
