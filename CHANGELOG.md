# Changelog

All notable changes to **DeckVault** are documented here.

## [v1.4.4] - 2026-11-18

### Android LibVLC Crop & Peek Pixel-Lock Fix, Target Clarity & 404 Render Fallback
- **Fixed `.ts`/`.avi` Small Centered Video + Mismatched Crop Overlay & Peek Jump (`VideoPlayerAndTrimmer.kt`)**:
  - Replaced `VLCVideoLayout` (`VideoHelper`'s broken portrait/landscape container bounds swap) with direct `android.view.TextureView` binding via `vlcVout.setVideoView(tv)` + `IVLCVout.OnNewVideoLayoutListener` and layout `setWindowSize` updates.
  - Sized both the active video surface (`TextureView` / `PlayerView`) and `VisualCropOverlay` inside the exact same centered aspect-fitted rectangle (`fitWDp × fitHDp`) across Split Stage, Side Rail, Fullscreen, and Peek toggle — zero black margins inside the crop frame and zero size jumps on Peek.
- **Fixed `HTTP 404: Unknown route` Render Errors & Explicit Execution Targets (`MainViewModel.kt`, `VideoStudioComponents.kt`, `MediaOptimizerSheet.kt`, `MainActivity.kt`)**:
  - Added clear execution target pills inside **Edit Studio**, **Media Optimizer**, and **Merge Videos** even when browsing remote PC or Android peer devices (`Run on <PC>` vs `Pull & Save on Device`, or `Pull & Save Here` vs `Offload: <PC>`), plus explicit primary action text (`Render on PC` vs `Render Here`).
  - Added automatic `HTTP 404` fallback in `MainViewModel.runRemoteHostJob`: falls back seamlessly to synchronous PC endpoints (`/api/video/edit`, `/api/media/*`) on `< v1.4` desktop companions, and stages & processes locally via `LocalMediaProcessor` into `Movies/DeckVault` when targeting Android peers or unsupported endpoints.
- *(Desktop Companion `DeckVault.exe` remains untouched on `v1.4.1` and 100% compatible.)*

## [v1.4.3] - 2026-11-18

### Android AVI/TS Studio Video Fix, TS Seekbar/Duration Fix & Flagship Phone Portrait UI
- **Fixed Black Video Preview on `.avi` & `.ts` in Edit Studio (`VideoPlayerAndTrimmer.kt`)**:
  - Switched LibVLC `VLCVideoLayout` rendering from `SurfaceView` (`attachViews(..., true)`) to `TextureView` (`attachViews(..., false)`) so video renders natively inside Compose hardware layers with full rotation, flip, zoom, and color `RenderEffect` support and zero paused surface blackouts.
  - Eliminated Compose `movableContentOf` AndroidView reparenting by keeping the Video Stage inside one fixed layout slot across Cinema, Split Stage, Side Rail, and Peek modes.
- **Fixed `.ts` Missing / Stuck `0s–1s` Seekbar (`VideoPlayerAndTrimmer.kt`, `LocalStorageRepository.kt`, `MainViewModel.kt`, `AndroidPeerServer.kt`)**:
  - Added `android.media.MediaExtractor` track duration + end-PTS scan fallback in `probeVideoMeta` for `.ts`/`.mts`/`.m2ts` containers when `MediaMetadataRetriever` returns `0`.
  - Added `/api/video/storyboard-info` in `AndroidPeerServer` + async background duration probing (`MainViewModel.openVideo` via local `MediaExtractor` or remote `/api/video/storyboard-info`) that updates duration, resolution, and Studio filmstrip frames live.
  - Wired LibVLC `Event.PositionChanged` + dynamic `(time / position)` duration derivation + `setPosition(fraction)` scrubbing whenever `vlcPlayer.length <= 0`, plus a real percentage seekbar fallback instead of clamping to fake `00:01`.
- **Responsive Flagship Phone & Z Fold Cover Screen Portrait Layout (`MediaBrowserPane.kt`, `MainActivity.kt`, `AllDrivesDashboard.kt`)**:
  - Replaced cramped multi-item single-row headers and squished segmented buttons on `< 600.dp` phones with a clean compact app bar, 1-tap horizontal **Device Switcher Pill Bar** (`This Phone`, paired PCs/tablets, `+ Pair`), flexible `weight(1f)` search + compact `Grid/List` toggle, unclipped filter chips, actionable empty states (`Browse Folders`, `All Drives`, `Clear Filters`, quick folders), and full-width mobile queue mini bar.
- *(Desktop Companion `DeckVault.exe` remains untouched on `v1.4.1` and 100% compatible.)*

## [v1.4.2] - 2026-11-18

### Android Hybrid Video Studio, Fitted 8-Handle Cropper & Full-Color Material 3 Themes
- **Hybrid Adaptive Video Studio (`VideoPlayerAndTrimmer.kt` & `VideoStudioComponents.kt`)**:
  - Replaced the overlapping Studio overlay with an adaptive split viewport: on Z Fold unfolded and phones in portrait, the video shrinks cleanly above the Studio workbench and single-row slim transport bar so 100% of the video frame stays unobstructed; on wide tablets (`>= 840dp` landscape), Studio docks as a right-hand inspector rail; added a **1-Tap Peek** button to collapse Studio down to a single slim bar for quick full-screen inspection, and preserved live ExoPlayer/LibVLC surfaces via `movableContentOf` across transitions.
  - Upgraded `VisualCropOverlay` to measure real rendered video bounds (`ExoPlayer.onVideoSizeChanged` and `LibVLC` track orientation/SAR dimensions) so the crop frame & Rule-of-Thirds grid never bleed into black letterbox/pillarbox bars, upgraded dragging to `rememberUpdatedState` (zero mid-drag cancellation), added **4 corners + 4 edge midpoint bars + center pan**, and added a live pixel resolution badge (`W × H`).
- **Full-Color Material 3 + Tonal Dark / OLED + Tonal / OLED Neon (`DeckVaultTheme.kt` & `Models.kt`)**:
  - Unlocked genuine Android 12–16 **Material You** dynamic surface & container tinting (`DYNAMIC_YOU`) instead of stripping it back to neutral grayscale, and added vibrant full-color palettes (`Electric Cobalt`, `Emerald Forest`, `Royal Amethyst`, `Crimson Velvet`, `Amber Sunburst`, `Cyber Teal`).
  - Added selectable **Dark Surface Styles**: **Tonal Dark** (rich color-tinted Material 3 dark hierarchy), **OLED + Tonal** (pure `#000000` backdrop + color-tinted Material cards), and **OLED Neon** (pure `#000000` backdrop + luminous borders & high-contrast accents) in both Settings and Quick Appearance sheet.
- *(Desktop Companion `DeckVault.exe` unchanged — 100% compatible with `v1.4.1`.)*

## [v1.4.1] - 2026-11-18

### Performance, FFmpeg Memory Spike Fix & Smoothness (`DeckVault.exe` & Android `v1.4.1`)
- **Eliminated 10 GB FFmpeg RAM Spike on Desktop**:
  - Rewrote `MediaService.get_or_build_storyboard` (`companion/deckvault_server.py`) to extract frames sequentially using a single `-threads 1 -skip_frame nokey` decoder and stitch tiles in Pillow (`PIL`), replacing the previous `16`-input parallel FFmpeg `xstack` decoder graph (~30 MB peak RAM vs. 3–4 GB+ per storyboard).
  - Bounded concurrent FFmpeg thumbnail/frame/storyboard jobs via `BoundedSemaphore(2)`, bounded `ffprobe` workers via `BoundedSemaphore(3)` + deduplicated queue, capped live playable H.264 stream threads (`-threads 4`), and switched `MetadataCache` to a persistent WAL SQLite connection.
  - Made `_handle_list` and `_handle_tree` return immediately without blocking on recursive `DiskIndexer` scans or synchronous `1.5s` `ffprobe` batches, and added `AbortController` request cancellation + `220ms` hover-dwell debounce on storyboards + stale `<img>` teardown + `decoding="async"` in `companion/desktop_ui.py`.
- **Android Access Error & Fast Browsing Fixes (`v1.4.1`)**:
  - Automatically requests `READ_MEDIA_VIDEO`/`IMAGES`/`AUDIO` (`READ_EXTERNAL_STORAGE` on older Android) at startup when needed, prioritizes `All Device Videos` / `Photos` / `Audio` when `MANAGE_EXTERNAL_STORAGE` isn't yet granted, and synthesizes accessible storage root folders (`DCIM`, `Movies`, `Pictures`, `Download`, `Documents`, `Music`) + path-prefix/bucket `MediaStore` fallback instead of throwing access error snackbars on tablets when opening `Internal Storage` or `SD Card`.
  - Replaced synchronous per-file `MediaMetadataRetriever` loops in `LocalStorageRepository.listDirectory` and `AndroidPeerServer /api/list` with a single fast batch `MediaStore` metadata cache prefetch (~10 ms), cached storage drive detection for `5s`, skipped deep subfolder scanning in large directories, added `Semaphore(2)` native codec bounds + Android Q+ hardware `loadThumbnail`, added parallel `resolveListFolder` + `resolveTree` with stale-job cancellation in `MainViewModel`, tuned OkHttp (`connectTimeout = 3s`, `maxRequestsPerHost = 12`) + Coil 3 `MemoryCache`/`DiskCache`, and added `contentType` reuse on `LazyVerticalGrid`/`LazyColumn`.
- **Wi-Fi Peer Architecture Overhaul & True SD Card Filtering (Option 1 + Option 2)**:
  - Fixed fake `"SD Card"` detection on phones without an SD card slot (Samsung Dual Messenger / Work Profile emulated secondary volumes) by requiring `vol.isRemovable && !vol.isEmulated`, `Environment.isExternalStorageRemovable()`, and distinct block-device `st_dev`.
  - Added persistent SQLite (`WAL`) metadata cache (`AndroidMetaCache`) shared between local storage and `AndroidPeerServer`, plus disk-cached `/api/image` thumbnails (`peer_img_thumbs/`) with fast embedded EXIF JPEG extraction (`ExifInterface.thumbnailBytes`) & Android Q+ hardware `loadThumbnail`.
  - Upgraded `AndroidPeerServer` to HTTP/1.1 persistent `Keep-Alive` (`backlog = 256`, `tcpNoDelay = true`), full `MediaStore` (`mediastore://`) & `content://` byte-range streaming parity over LAN, `WifiLock` (`WIFI_MODE_FULL_LOW_LATENCY`) + optional Unrestricted Battery button in [HostsModal.kt](file:///C:/DeckVault/android/app/src/main/java/com/deckvault/app/ui/components/HostsModal.kt), multi-IP failover with automatic `hostId` UDP IP self-healing in both `DeckVaultApi` and `companion/deckvault_server.py` (`FleetHttpPool` HTTP keep-alive proxy), and instant stale-while-revalidate root/folder caching in `MainViewModel`.

## [v1.4.0] - 2026-11-16

### Windows Desktop Studio (`DeckVault.exe` v1.4.0)
- **Window-Wide Loading & Upload Progress Bars**: Added a top-level progress bar strip beneath the title bar (`#globalLoadBar`), video player stream buffering bar (`#vidBufferBar`), and byte-accurate `XMLHttpRequest` upload progress tracking across every loading, transfer, edit, compress, upscale, and merge action.
- **Info Tab Render Queue Drawer (`#queueDock`)**:
  - Added a persistent sequential background `JobManager` (`GET /api/jobs`, `POST /api/jobs/enqueue`, `/api/jobs/cancel`, `/api/jobs/clear`) pinned cleanly at the bottom of the right **Info & Queue** panel.
  - Added **`+ Add to Queue`** AND **`Run Now`** actions to **Cut & Edit Studio**, **Compressor**, **Upscaler & Enhancer**, **Extract Audio**, **Export GIF**, and **Merge Videos** — every task automatically tracks real-time FFmpeg `%`, speed (`x`), and ETA (`s left`) with instant **Cancel**.
  - Added direct post-completion shortcuts right on completed queue cards: **`▶ Play / Open`**, **`✂ Edit / Cut`**, **`🗜 Compress`**, **`✨ Upscale`**, and **`📂 Folder`** (jumps directly to the finished file's folder and focuses it).
- **Upgraded Upscaler & New Video Editing Tools**:
  - Added Upscaler Engine Modes: **Crisp CAS** (`lanczos` + Contrast Adaptive Sharpen `cas`), **Anime / Line-Art**, and **Restore & Clean** (`deband` + `nlmeans` / `hqdn3d` denoise), plus optional **Camera Stabilization (`deshake`)**, **De-banding**, and **60 FPS** output.
  - Expanded **Cut & Edit Studio** with live **Brightness / Contrast / Saturation** sliders (`eq`), **CAS Sharpen**, **Deband**, **Stabilize**, **Fade In/Out**, **Loudness Normalization (`loudnorm`)**, and **Export Speed (`0.5x–2.0x`)**.
  - Added **Extract Audio (`M4A`/`MP3`)**, **Export Animated GIF** (`palettegen`/`paletteuse`), and multi-select **Merge Videos** (automatically normalizing aspect ratio, frame rate, and audio across clips).
- **Android Mobile Offload Queue Support**: Added `/api/offload/stage`, `/api/offload/enqueue`, and `/api/offload/cleanup` staging endpoints to `deckvault_server.py` so Android devices can offload heavy edits/upscales/compressions/merges to the PC companion and automatically pull the finished media back to internal/SD storage.

### Android App (`com.deckvault.app` v1.4.0)
- **Cinema Edge-to-Edge Auto-Hiding Video Player & Default System Player**:
  - Switched playback HUD to full-bleed edge-to-edge translucent overlays that **automatically fade out after 2.8 seconds** (with immersive hidden system bars via `WindowInsetsControllerCompat` and `TextureView` hardware rendering); single tap toggles controls cleanly.
  - Added full multi-touch & gesture controls: **horizontal swipe-to-seek** with live time HUD, **left-edge vertical brightness swipe**, **right-edge vertical volume + 200% `LoudnessEnhancer` software boost**, **double-tap** skip/pause, **long-press hold `2x` speed boost**, and **pinch-to-zoom & pan (`1x–5x`)**.
  - Added `ACTION_VIEW` manifest intent filters (`video/*`, `audio/*`, `image/*`, `application/pdf`, `text/*`) so DeckVault can be selected and set as the default media player on Android; added PiP mode, screen lock, orientation lock (`Auto/Landscape/Portrait`), aspect modes (`Fit`, `Fill`, `Stretch`, `16:9`, `9:16`, `4:3`), mirror flip, frame step (`-1f`/`+1f`), `A-B Loop`, audio/subtitle track selector + sibling `.srt`/`.vtt` auto-loading + external subtitle picker, sleep timer, frame snapshot, resume playback memory, and PC live H.264 stream fallback.
- **Direct On-Device & SD Card Storage Engine (Fixed Local Tablet Storage Error)**:
  - Replaced loopback-HTTP local access with direct `LocalStorageRepository`: **`This Device`** now mounts automatically on startup with `StorageManager.storageVolumes` volume detection (`Internal Storage`, `SD Card`, `USB`), `MediaStore` media collections (`All Device Videos` / `Photos` / `Audio`) when All Files Access isn't yet granted, cross-volume **Internal ↔ SD Card ↔ USB** copy/move with verified transfers and persisted **SAF `DocumentFile` (`OpenDocumentTree`)** write fallback, and dual delete (`Trash/Recycle .deckvault_trash` vs `Permanently Delete`).
- **On-Device Media Studio, Optimizer, Merge & PC Offload Render Queue**:
  - Added `LocalMediaProcessor` (`media3-transformer` + `media3-effect`) for offline hardware Trim/Cut, Visual Crop Box, Rotate (`90°`), Flip H/V, Speed, Color adjustments (`Brightness`/`Contrast`/`Saturation`), Mute, Compressor (target MB / H.264 / HEVC / live size estimate), Upscaler, Extract Audio (`M4A`), and multi-select **Merge Videos**.
  - Added top window progress indicators, persistent bottom **Render Queue** (`+ Add to Queue` / `Run Now`, `Cancel`, and `Play` / `Edit Studio` / `Folder` shortcuts), and seamless **PC Companion Offload** to push local tablet/SD files to paired PC FFmpeg (unlocking `Anime`, `Restore`, `HQ NLMeans Denoise`, `60 FPS`, `Deband`, `Stabilize`, `GIF`, `Loudnorm`) and download the result straight back to tablet/SD storage.
  - Added **Direct `.avi` / `.ts` / Multi-Codec Native Playback (`v1.4.3`)**:
    - Integrated **LibVLC `3.7.7` Native Universal Playback Engine** alongside **ExoPlayer + NextLib FFmpeg** (`NextRenderersFactory` with `EXTENSION_RENDERER_MODE_PREFER` + `AudioAttributes.DEFAULT`), resolving silent Bandicam `.avi` (`mp2` audio tag `0x0050`), ADPCM, WMA, `.ts`, `.wmv`, `.flv`, `.vob`, and `.mpg` playback across **Local Device Storage**, **Android-to-Android Peer Streaming**, and **Laptop/PC Hosts** without requiring server transcoding.
    - Added automatic fallback from ExoPlayer to LibVLC on missing/unsupported audio tracks or codec errors (plus PC H.264+AAC `/api/stream/playable` fallback as a final layer) and a live **Decoder (`VLC` / `Exo`)** switcher chip in the player control bar; enabled `arm64-v8a` + `armeabi-v7a` ABI packaging (`16 KB` page-aligned native libraries) and R8 ProGuard keep rules.

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
