# Changelog

All notable changes to **DeckVault** are documented here.

## [v1.3.0] - 2026-11-15

### Windows Desktop Studio (`DeckVault.exe` v1.3.0)
- **Built-In Compressor & Upscaler Studio (`#optimizeModal`)**:
  - Added **Compressor** with real-time size prediction (analytical, target MB, or `1.5s Sample Probe` test encode), resolution presets (`Original / 1080p / 720p / 480p`), H.264 / HEVC codec, CRF quality slider, audio bitrate/mute, and copy/overwrite save modes.
  - Added **Upscaler & Enhancer** for videos & images (`2x` / `4K` / `1440p` / `1080p` Lanczos scaling, contrast-adaptive sharpening, optional `hqdn3d` temporal denoise, and `60 FPS` motion interpolation).
- **Cloud & Storage Apps Tab**: Added a dedicated Cloud & Storage Apps bar under Devices in the left panel with a Google Drive tab (`I:\My Drive` / virtual drive detection) plus auto-detected & user-connectable `Dropbox`, `OneDrive`, `iCloud Drive`, `MEGA`, `Box`, `Nextcloud`, and custom/NAS folders.
- **Dual Delete & Handle-Lock Fix**: Added `drive_type == 4` virtual/network drive support in `get_roots()`, handle-release (`v.load()`) + retry logic in `/api/file/delete`, separate **`Move to Recycle Bin (Del)`** and **`Permanently Delete (Shift+Del)`** actions across all menus, and automatic prompt fallback to Permanent Delete when a drive lacks Recycle Bin support.
- **Multi-Select, Split View & External Drag-Out**:
  - Added card checkboxes, **`Select` mode** toggle, `Shift+Click` range selection, and rubber-band **marquee box selection** on empty space.
  - Added resizable **Split View** dual-pane browser with right-click **`Open in Split View`** on devices, Cloud tabs, drives, folders, and favorites, plus cross-pane Copy/Move.
  - Added native **OLE FileDrop drag-out** (`DownloadURL` + `DesktopWindowApi.start_native_drag`) and **`Ctrl+C` Windows `CF_HDROP` clipboard** support to drop or paste files straight into Dropbox, Explorer, or external apps.
- **Updater & Live Endpoint**: Added localhost `POST /api/desktop/quit` endpoint and sibling-`companion/` priority loader cleanly in `desktop_launcher.py`.

## [v1.2.0] - 2026-11-15

### Windows Desktop App (`DeckVault.exe`) & Web Studio
- **Unified Native Desktop App**: Built standalone Windows app (`DeckVault.exe` + `_internal/`) using PyWebView (EdgeChromium) + embedded tray/LAN server, with custom multi-resolution icon, flush OLED matte frameless title bar (`-`, `[]`, `X`), draggable 8-edge window resize grips, `AppUserModelID`, Windows Start Menu shortcut, and single-instance window focus/zombie-process recovery.
- **OLED Material Expressive File Browser**: Added adjustable multi-panel layout (Navigation Rail, Folder Disk-Usage Tree with cached recursive folder sizes, main Gallery/List media grid with `1–8` column density & card aspect ratio controls, Inspector sidebar), **All Drives & Devices** fleet dashboard, **Space Hogs** recursive largest-video finder, and **Move Up / Parent Folder** navigation.
- **VLC-Grade Video Player & Cut/Crop Studio**:
  - Added playback speed (`0.25x`–`4.0x`), 200% Web Audio volume boost, `Fit / Fill (Zoom) / Stretch`, `9:16` / `16:9` / `4:3` / `1:1` aspect overrides, frame-by-frame stepping (`,` / `.`), `A-B Loop`, 16-frame storyboard scrub sheet with hover thumbnails, and external VLC launcher.
  - Added live fragmented-H.264 stream fallback (`/api/stream/playable`) so HEVC, high-fps (`150fps+`), MKV, and WMV videos play smoothly without black screens.
  - Added **Interactive Visual Crop Box** (`C`), aspect presets (`Free`, `16:9`, `9:16`, `1:1`, `4:3`), live `Rotate 90°`, `Flip H`, `Flip V`, audio mute export, `Save Over Original` / `Save as Copy (_edited.mp4)`, PNG frame snapshot, and FFmpeg pipeline (`transpose` / `flip` before `crop` with `setsar=1`).
  - Added **Draggable Cut Start (`In`) & End (`Out`) Bars** directly on the storyboard strip with live handle timestamps, playhead scrubbing, and automatic **A-B loop + Cropped Preview playback (`P`)** on release.
  - Added automatic `/api/ping` `videoEditOrder` capability check with inverse crop coordinate adaptation (`toLegacyPreTransformCrop`) when editing videos hosted on older un-updated remote companions.
- **Google Drive Backup & Cross-Device Favorites**: Added one-click backup destination picker (`I:\My Drive\DeckVault Backup` / detected Google Drive Desktop mount or `rclone` remote) and persistent favorite folders across paired devices (`favorites.json`).

### Android App (`com.deckvault.app` v1.2.0)
- **Adaptive Tablet & Z Fold UI**: Pitch-black `#000000` OLED Compose Material 3 Expressive interface tailored for Galaxy Tab S8 Ultra and Z Fold 5, supporting `1–8` grid columns, card aspect ratios, sorting/filtering, Space Hogs, All Drives dashboard, and 5 OLED accent palettes + Dynamic Material You.
- **Multi-Device LAN Fleet & Background Peer Service**: Added UDP LAN auto-discovery (`48721`), 6-digit PIN pairing, multi-host switching, cross-device copy/move/rename/delete, built-in `DeckVaultPeerService` foreground server + `DeckVaultWebPortal` fallback so PCs and other mobile devices can browse Android storage cleanly.
- **ExoPlayer & Video Trimmer**: Built-in ExoPlayer streaming with byte-range seeking, playback speed control, 16-frame S Pen / touch scrub previews, and visual filmstrip cutter (`/api/video/trim`).

### iOS Companion (`DeckVaultApp`)
- **SwiftUI Universal Client & Peer Server**: Added initial SwiftUI browser + `DeckVaultPeerServer` (`HTTP` + `UDP` discovery) scaffold for iPhone/iPad photo library and local folder sharing.

## [v1.0.0] - Initial Release
- Initial Windows Python LAN Companion (`deckvault_server.py`) with folder size indexing, storyboard sprite sheets, FFmpeg trimming, and Android LAN file browser.
