# DeckVault — Multi-Device OLED Media Studio & LAN File Fleet

**DeckVault** turns your Windows PC, Windows Laptop, Android Phone/Tablet, and iOS devices into a unified pitch-black (`#000000` OLED) file browser, video studio, and cross-device transfer hub over local Wi-Fi — with no cloud upload required.

---

## Features

### Windows Desktop App & Companion Server (`companion/`, `DeckVault.exe`)
- **Unified Multi-Device Browser**: Browse, search, upload, copy, move, rename, recycle/delete files seamlessly across your local PC and any paired Windows, Android, or iOS device on your LAN.
- **Storage Analytics**: Cached recursive folder sizes with proportional usage bars, **All Drives & Devices** overview, and **Space Hogs** recursive largest-video finder.
- **VLC-Grade Video Player & Cut/Crop Studio**:
  - Speed control (`0.25x`–`4.0x`), 200% audio boost, `Fit / Fill / Stretch`, `9:16` vertical & widescreen aspect toggles, frame-step (`,` / `.`), `A-B Loop`, 16-frame hover storyboards, and automatic live H.264 transcoding for HEVC / high-FPS / MKV files.
  - Visual **Crop Box** (`C`), aspect presets (`16:9`, `9:16`, `1:1`, `4:3`, `Free`), live `Rotate 90°`, `Flip H/V`, mute audio export, and PNG snapshots.
  - **Draggable Cut Start/End Bars** directly on the storyboard strip with automatic loop + **Cropped Preview (`P`)** playback on release before rendering.
  - Fast lossless copy cuts (`ffmpeg -c copy`) when trimming without visual transforms, or compressed H.264 re-encode (`CRF 16–32`) saving over original (with Recycle Bin backup) or as `_edited.mp4`.
- **Built-In Compressor, Upscaler & Render Queue**: Shrink videos/images with live Before → After size estimates (`1.5s` FFmpeg sample probe or exact Target MB, H.264/HEVC), upscale & restore footage (`Crisp CAS`, `Anime`, `Restore + NLM Denoise`, `deband`, `deshake` stabilization, `60 FPS`), track every action with window-wide loading/upload progress bars, and queue or monitor jobs in the **Info & Queue** bottom drawer with direct `Play`, `Edit`, `Compress`, `Upscale`, and `Go to Folder` shortcuts.
- **Audio Extract, Animated GIF & Merge Videos**: Export trimmed audio (`M4A`/`MP3` with `loudnorm`), create palette-optimized GIF clips, or merge multiple heterogeneous video clips into a single normalized MP4; plus Cut/Crop Studio color sliders (Brightness/Contrast/Saturation), Fade In/Out, Speed, and Stabilization.
- **Cloud & Storage Apps Tabs**: Browse **Google Drive** as its own tab under Devices and connect **Dropbox**, **OneDrive**, **iCloud Drive**, **MEGA**, or custom network folders (`Open`, `Split View`, drag-and-drop).
- **Split View, Multi-Select & External App Drag-Out**: Right-click any device, cloud tab, drive, or folder to **Open in Split View**; multi-select via checkboxes, `Shift+Click`, or marquee drag box; drag files directly out of DeckVault (or press `Ctrl+C`) to drop/paste into Dropbox or Explorer; choose between **Move to Recycle Bin (`Del`)** and **Permanently Delete (`Shift+Del`)**.
- **Cross-Device Favorites & Google Drive Backup**: Bookmark folders from any device and back up files straight to Google Drive for Desktop or `rclone` remotes.
- **Single-Instance Desktop Window + Tray Server**: Runs cleanly in the Windows system tray (`48720` HTTP / `48721` UDP) so paired devices stay connected while the frameless OLED studio window opens on demand.

### Android App (`android/` — `com.deckvault.app` v1.4.1)
- Native Kotlin + Jetpack Compose Material 3 Expressive (`#000000` OLED) app tailored for tablets, **Galaxy Z Fold**, and Android phones — usable both **standalone offline** and **paired with your PCs over Wi-Fi**.
- **Default Cinema Video Player**: Full-bleed edge-to-edge player (`ACTION_VIEW` default video/file player intents) powered by a **Dual Playback Engine (LibVLC 3.7.7 + ExoPlayer NextLib FFmpeg)** so **every container & audio codec** (`.avi` incl. Bandicam MP2/ADPCM/WMA, `.ts`, `.wmv`, `.flv`, `.vob`, `.mkv`, `.mp4`, `.mov`) plays natively with full sound locally, Android-to-Android, and from PC/Laptop; includes auto-hiding HUD & immersive system bars, horizontal **swipe-to-seek**, left/right **brightness & 200% volume boost swipes**, double-tap skip, hold-for-`2x`, pinch-to-zoom, aspect modes (`Fit / Fill / Stretch / 16:9 / 9:16 / 4:3`), 1-tap `VLC / Exo` decoder switch, PiP, lock screen, orientation toggle, frame stepping, `A-B Loop`, `.srt`/`.vtt` subtitles, sleep timer, frame snapshots, resume memory, and PC live H.264 stream fallback.
- **On-Device & SD Card Storage Manager**: Direct `This Device` browsing across **Internal Storage**, **removable SD Cards**, and **USB drives** (`StorageManager` + `MediaStore` fallback + persisted SAF `DocumentFile` writes for cross-volume **Internal ↔ SD Card** Move/Copy and dual **Trash vs Permanent Delete**).
- **On-Device Studio, Optimizer & PC Offload Render Queue**: Built-in `media3-transformer` Cut, Visual Crop Box, Rotate/Flip, Color grading, Compressor, Upscaler, Audio extraction, and multi-select Merge Videos — with live progress bars, unified Queue (`+ Add to Queue` / `Run Now`, `Play` / `Studio` / `Folder` shortcuts), and optional **PC Companion Offload** that sends tablet/SD tasks to desktop FFmpeg and automatically saves outputs back to your device.
- **Foreground LAN Peer Server** (`DeckVaultPeerService`) + web portal (`48720`/`48721`) so your PCs can browse Internal Storage & SD Card over Wi-Fi.

### iOS App (`ios/`)
- SwiftUI browser and `DeckVaultPeerServer` scaffold for sharing iOS local/Documents media on the same LAN fleet.

---

## Quick Start (Prebuilt Release v1.4.0)

Download the latest binaries from the **Releases** page:

1. **Windows PC / Laptop (`DeckVault-app.zip`)**
   - Extract the entire `app\` folder (keep `_internal\` alongside `DeckVault.exe`) and launch `DeckVault.exe` (or `DeckVault.exe --tray` for background server only).
   - **Install FFmpeg** (required for thumbnails, storyboards, H.264 live transcode playback, and Cut/Rotate/Crop):
     ```powershell
     winget install -e --id Gyan.FFmpeg
     ```
   - **Allow Windows Firewall** on Private Wi-Fi (`TCP 48720`, `UDP 48721`) when prompted, or run `companion\install_firewall_rule.ps1`.
   - Local settings, paired hosts, PIN, and favorites are stored in `%USERPROFILE%\.deckvault\`.

2. **Android (`app-release.apk`)**
   - Install `app-release.apk` (Android 9+ / API 28+), grant **All files access** (`MANAGE_EXTERNAL_STORAGE`) so your drives/folders can be browsed, and open **Devices** to auto-discover or enter your PC's IP + 6-digit PIN.

> Make sure your PC, laptop, and mobile devices are on the same **Private Wi-Fi** network (disable AP/Client Isolation on guest Wi-Fi).

---

## Build from Source

### Windows Desktop (`DeckVault.exe`)
Requires Python 3.10+, `pywebview`, `pystray`, `pillow`, and `pyinstaller`:

```powershell
pip install pywebview pystray pillow pyinstaller
pyinstaller --noconfirm --clean --onedir --windowed --name DeckVault `
  --icon companion\deckvault.ico `
  --add-data "companion\deckvault.ico;." `
  --add-data "companion\desktop_ui.py;." `
  --add-data "companion\deckvault_server.py;." `
  --exclude-module numpy --exclude-module scipy --exclude-module matplotlib `
  --exclude-module pygame --exclude-module PyQt6 --exclude-module PyQt5 `
  companion\desktop_launcher.py
```

Or run the companion directly from Python:
```powershell
python companion\deckvault_server.py --app
```

### Android APK
Requires JDK 17+ / 21 and Android SDK (or Android Studio):
```powershell
cd android
.\build_apk.ps1
```
*(Or run `.\gradlew.bat assembleRelease` — a local keystore `app/deckvault-release.jks` is generated automatically by `build_apk.ps1` and ignored by git.)*

### iOS (Xcode)
See [ios/README_iOS.md](ios/README_iOS.md).

---

## Architecture & Security

- **Local-First LAN Auth**: Each node generates a random 6-digit pairing PIN and per-client bearer tokens (`X-DeckVault-Token`) stored only locally in `~/.deckvault/` (Windows) or `EncryptedSharedPreferences` / app storage (Android).
- **Safe Destructive Actions**: Deleted files go to the OS Recycle Bin by default, and overwrite edits use atomic temp-file replacement after FFmpeg output verification.

See [CHANGELOG.md](CHANGELOG.md) for full release notes.
