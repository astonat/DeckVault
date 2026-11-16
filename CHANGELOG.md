# Changelog

All notable changes to **DeckVault** are documented here.

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
