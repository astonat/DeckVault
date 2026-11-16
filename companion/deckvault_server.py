"""DeckVault PC & Laptop Companion Server.

Provides fast disk-usage indexing, media metadata probing, video thumbnail &
storyboard sprite generation, HTTP byte-range media streaming, LAN UDP
auto-discovery, and atomic lossless/compressed video trimming that overwrites
the original file (optionally moving the replaced original to the Recycle Bin).
"""

from __future__ import annotations

import argparse
import ctypes
from ctypes import wintypes
import concurrent.futures
import gc
import hashlib
import io
from http import HTTPStatus
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
import json
import mimetypes
import os
from pathlib import Path
import queue
import secrets
import shutil
import socket
import sqlite3
import subprocess
import sys
import threading
import time
from typing import Any
from urllib.parse import parse_qs, quote as url_quote, urlparse
import urllib.error
import urllib.request
import uuid
from desktop_ui import DESKTOP_APP_HTML


APP_VERSION = "1.4.0"
DEFAULT_HTTP_PORT = 48720
DEFAULT_UDP_PORT = 48721
DISCOVERY_MAGIC = b"DECKVAULT_DISCOVER_V1"
SUBPROCESS_FLAGS = getattr(subprocess, "CREATE_NO_WINDOW", 0) if os.name == "nt" else 0

VIDEO_EXTS = {
    ".mp4", ".mkv", ".mov", ".avi", ".webm", ".m4v", ".wmv", ".flv",
    ".ts", ".m2ts", ".mts", ".3gp", ".mpg", ".mpeg", ".vob",
}
IMAGE_EXTS = {
    ".jpg", ".jpeg", ".png", ".webp", ".gif", ".bmp", ".heic", ".heif", ".avif", ".tiff",
}
AUDIO_EXTS = {
    ".mp3", ".flac", ".wav", ".m4a", ".aac", ".ogg", ".opus", ".wma",
}

APP_DIR = Path(sys.executable).resolve().parent if getattr(sys, "frozen", False) else Path(__file__).resolve().parent
CONFIG_DIR = Path.home() / ".deckvault"
CONFIG_PATH = CONFIG_DIR / "config.json"
CACHE_DB_PATH = CONFIG_DIR / "cache.db"
THUMBS_DIR = CONFIG_DIR / "thumbs"
STORYBOARDS_DIR = CONFIG_DIR / "storyboards"
FRAMES_DIR = CONFIG_DIR / "frames"


def detect_windows_drives() -> list[str]:
    """Return fixed/removable/cloud Windows drive roots such as ['C:\\', 'D:\\', 'I:\\']."""
    if os.name != "nt":
        return [str(Path.home())]
    drives: list[str] = []
    bitmask = ctypes.windll.kernel32.GetLogicalDrives()
    for i in range(26):
        if bitmask & (1 << i):
            letter = chr(ord("A") + i)
            root = f"{letter}:\\"
            drive_type = ctypes.windll.kernel32.GetDriveTypeW(root)
            # 2 = removable, 3 = fixed, 4 = network / cloud virtual drive (e.g. Google Drive)
            if drive_type in (2, 3, 4) and os.path.exists(root):
                drives.append(root)
    return drives or [str(Path.home())]


def get_drive_volume_label(drive_root: str) -> str:
    """Return the Windows volume label for a drive root, or empty string."""
    if os.name != "nt":
        return ""
    buf = ctypes.create_unicode_buffer(261)
    res = ctypes.windll.kernel32.GetVolumeInformationW(
        drive_root, buf, len(buf), None, None, None, None, 0
    )
    return buf.value if res else ""


def send_to_recycle_bin(file_path: str) -> None:
    """Move a file or folder to the Windows Recycle Bin silently using SHFileOperationW."""
    clean_path = os.path.abspath(file_path).rstrip("\\/")
    if len(clean_path) == 2 and clean_path[1] == ":":
        clean_path += "\\"
    if os.name != "nt":
        if os.path.isdir(clean_path):
            shutil.rmtree(clean_path)
        else:
            os.remove(clean_path)
        return

    class SHFILEOPSTRUCTW(ctypes.Structure):
        _fields_ = [
            ("hwnd", wintypes.HWND),
            ("wFunc", wintypes.UINT),
            ("pFrom", wintypes.LPCWSTR),
            ("pTo", wintypes.LPCWSTR),
            ("fFlags", ctypes.c_uint16),
            ("fAnyOperationsAborted", wintypes.BOOL),
            ("hNameMappings", ctypes.c_void_p),
            ("lpszProgressTitle", wintypes.LPCWSTR),
        ]

    FO_DELETE = 0x0003
    FOF_SILENT = 0x0004
    FOF_NOCONFIRMATION = 0x0010
    FOF_ALLOWUNDO = 0x0040
    FOF_NOERRORUI = 0x0400

    # Double null-terminated path list required by SHFileOperationW
    double_null_path = clean_path + "\0\0"
    last_rc = 0
    for attempt in range(3):
        op = SHFILEOPSTRUCTW()
        op.hwnd = None
        op.wFunc = FO_DELETE
        op.pFrom = double_null_path
        op.pTo = None
        op.fFlags = FOF_ALLOWUNDO | FOF_NOCONFIRMATION | FOF_NOERRORUI | FOF_SILENT
        op.fAnyOperationsAborted = False
        op.hNameMappings = None
        op.lpszProgressTitle = None
        last_rc = ctypes.windll.shell32.SHFileOperationW(ctypes.byref(op))
        if last_rc == 0 and not op.fAnyOperationsAborted:
            return
        if attempt < 2:
            gc.collect()
            time.sleep(0.15)
    raise OSError(f"Recycle Bin failed (code {last_rc}) — drive may not support Recycle Bin; use Permanently Delete")


def _resolve_unique_dest(dest_dir: str, raw_name: str, conflict: str = "rename") -> str | None:
    """Resolve destination file path inside dest_dir honoring conflict policy."""
    clean_name = os.path.basename(raw_name.strip())
    if not clean_name or clean_name in (".", ".."):
        raise ValueError("Invalid filename")
    candidate = os.path.join(dest_dir, clean_name)
    if not os.path.exists(candidate) or conflict == "overwrite":
        return candidate
    if conflict == "skip":
        return None
    stem, ext = os.path.splitext(clean_name)
    idx = 1
    while os.path.exists(candidate):
        candidate = os.path.join(dest_dir, f"{stem} ({idx}){ext}")
        idx += 1
    return candidate


def _count_tree_stats(root_dir: str) -> tuple[int, int, int]:
    """Return (total_bytes, file_count, media_count) inside root_dir."""
    total_b, files_n, media_n = 0, 0, 0
    for cur, _, fnames in os.walk(root_dir):
        for fn in fnames:
            fp = os.path.join(cur, fn)
            try:
                total_b += os.path.getsize(fp)
                files_n += 1
                if classify_media_type(fp) in ("video", "image"):
                    media_n += 1
            except OSError:
                pass
    return total_b, files_n, media_n


def _safe_copy_or_move_item(src: str, dst: str, move: bool) -> None:
    """Safely move or copy a file/directory across folders or drives."""
    is_dir = os.path.isdir(src)
    parent = os.path.dirname(dst)
    if is_dir:
        if move:
            try:
                os.replace(src, dst)
                return
            except OSError:
                pass
            shutil.copytree(src, dst, dirs_exist_ok=True)
            shutil.rmtree(src)
        else:
            shutil.copytree(src, dst, dirs_exist_ok=True)
        return

    if move:
        try:
            os.replace(src, dst)
            return
        except OSError:
            pass

    tmp_dst = os.path.join(parent, f".deckvault_tmp_{uuid.uuid4().hex}")
    try:
        shutil.copy2(src, tmp_dst)
        os.replace(tmp_dst, dst)
    except Exception:
        if os.path.exists(tmp_dst):
            try:
                os.remove(tmp_dst)
            except OSError:
                pass
        raise

    if move:
        os.remove(src)


def get_local_ipv4_addresses() -> list[str]:
    """Return non-loopback local IPv4 addresses sorted with typical LAN subnets first."""
    ips: list[str] = []
    try:
        hostname = socket.gethostname()
        for info in socket.getaddrinfo(hostname, None, socket.AF_INET):
            ip = info[4][0]
            if not ip.startswith("127.") and ip not in ips:
                ips.append(ip)
    except OSError:
        pass

    try:
        with socket.socket(socket.AF_INET, socket.SOCK_DGRAM) as s:
            s.connect(("8.8.8.8", 80))
            primary = s.getsockname()[0]
            if primary not in ips and not primary.startswith("127."):
                ips.insert(0, primary)
            elif primary in ips:
                ips.remove(primary)
                ips.insert(0, primary)
    except OSError:
        pass

    return ips or ["127.0.0.1"]


def classify_media_type(path: str) -> str:
    """Classify a file extension into video, image, audio, or other."""
    ext = os.path.splitext(path)[1].lower()
    if ext in VIDEO_EXTS:
        return "video"
    if ext in IMAGE_EXTS:
        return "image"
    if ext in AUDIO_EXTS:
        return "audio"
    return "other"


def find_binary(name: str) -> str | None:
    """Locate ffmpeg or ffprobe on PATH or common Windows install paths."""
    found = shutil.which(name)
    if found:
        return found
    candidates = [
        rf"C:\ffmpeg\bin\{name}.exe",
        rf"C:\Program Files\ffmpeg\bin\{name}.exe",
        str(Path.home() / "AppData/Local/Microsoft/WinGet/Links" / f"{name}.exe"),
    ]
    for c in candidates:
        if os.path.isfile(c):
            return c
    return None


class ServerConfig:
    """Persistent configuration and token registry for the companion server."""

    def __init__(self) -> None:
        self._lock = threading.Lock()
        self.data: dict[str, Any] = {}
        self.load_or_init()

    def load_or_init(self) -> None:
        CONFIG_DIR.mkdir(parents=True, exist_ok=True)
        THUMBS_DIR.mkdir(parents=True, exist_ok=True)
        STORYBOARDS_DIR.mkdir(parents=True, exist_ok=True)
        FRAMES_DIR.mkdir(parents=True, exist_ok=True)

        if CONFIG_PATH.exists():
            with open(CONFIG_PATH, "r", encoding="utf-8") as f:
                self.data = json.load(f)
        else:
            self.data = {}

        changed = False
        defaults = {
            "host_id": str(uuid.uuid4()),
            "device_name": socket.gethostname(),
            "http_port": DEFAULT_HTTP_PORT,
            "udp_port": DEFAULT_UDP_PORT,
            "pin": f"{secrets.randbelow(900000) + 100000}",
            "tokens": [],
            "roots": detect_windows_drives(),
            "space_saved_bytes": 0,
        }
        for k, v in defaults.items():
            if k not in self.data:
                self.data[k] = v
                changed = True

        if changed:
            self.save()

    def save(self) -> None:
        with self._lock:
            tmp = CONFIG_PATH.with_suffix(".tmp")
            with open(tmp, "w", encoding="utf-8") as f:
                json.dump(self.data, f, indent=2)
            os.replace(tmp, CONFIG_PATH)

    def verify_pin_and_issue_token(self, pin: str, client_name: str) -> str | None:
        with self._lock:
            if str(pin).strip() != str(self.data["pin"]).strip():
                return None
            token = secrets.token_urlsafe(32)
            tokens: list[dict[str, Any]] = self.data.setdefault("tokens", [])
            tokens.append({
                "token": token,
                "client": client_name,
                "created_at": int(time.time()),
            })
            # Keep last 20 tokens
            self.data["tokens"] = tokens[-20:]
        self.save()
        return token

    def is_valid_token(self, token: str | None) -> bool:
        if not token:
            return False
        with self._lock:
            return any(item.get("token") == token for item in self.data.get("tokens", []))

    def rotate_pin(self) -> str:
        with self._lock:
            new_pin = f"{secrets.randbelow(900000) + 100000}"
            self.data["pin"] = new_pin
        self.save()
        return new_pin

    def add_space_saved(self, delta_bytes: int) -> int:
        if delta_bytes <= 0:
            return int(self.data.get("space_saved_bytes", 0))
        with self._lock:
            total = int(self.data.get("space_saved_bytes", 0)) + int(delta_bytes)
            self.data["space_saved_bytes"] = total
        self.save()
        return total

    def get_roots(self) -> list[str]:
        candidates: list[str] = [
            *(self.data.get("roots", []) or []),
            *detect_windows_drives(),
            str(Path.home()),
        ]
        for prov in (self.data.get("cloud_providers", []) or []):
            p = str((prov or {}).get("path", "")).strip()
            if p:
                candidates.append(p)
        gd_dir = str(self.data.get("gdrive_backup_dir", "") or "").strip()
        if gd_dir:
            candidates.append(gd_dir)

        seen: set[str] = set()
        roots: list[str] = []
        for raw in candidates:
            norm = os.path.normpath(str(raw))
            if len(norm) == 2 and norm[1] == ":":
                norm += "\\"
            key = norm.lower()
            if key not in seen and os.path.exists(norm):
                seen.add(key)
                roots.append(norm)
        return roots or detect_windows_drives()

    def resolve_safe_path(self, raw_path: str) -> str:
        """Normalize and ensure requested path resides inside an allowed root."""
        if not raw_path:
            raise PermissionError("Empty path")
        norm = os.path.normpath(raw_path)
        # Handle bare drive like 'C:' -> 'C:\\'
        if len(norm) == 2 and norm[1] == ":":
            norm = norm + "\\"
        abs_target = os.path.abspath(norm)
        roots = [os.path.abspath(r) for r in self.get_roots()]
        for root in roots:
            try:
                if os.path.commonpath([abs_target.lower(), root.lower()]) == root.lower():
                    return abs_target
            except ValueError:
                continue
        raise PermissionError(f"Path outside allowed roots: {abs_target}")


class MetadataCache:
    """Thread-safe SQLite cache for ffprobe video/image metadata."""

    def __init__(self, db_path: Path) -> None:
        self._db_path = db_path
        self._lock = threading.Lock()
        self._init_db()

    def _connect(self) -> sqlite3.Connection:
        return sqlite3.connect(self._db_path, timeout=15)

    def _init_db(self) -> None:
        from contextlib import closing
        with self._lock, closing(self._connect()) as conn:
            conn.execute("""
                CREATE TABLE IF NOT EXISTS media_meta (
                    path TEXT PRIMARY KEY,
                    mtime_ns INTEGER NOT NULL,
                    size_bytes INTEGER NOT NULL,
                    duration_ms INTEGER NOT NULL,
                    width INTEGER NOT NULL,
                    height INTEGER NOT NULL,
                    fps REAL NOT NULL,
                    video_codec TEXT NOT NULL,
                    audio_codec TEXT NOT NULL,
                    bitrate INTEGER NOT NULL
                )
            """)
            conn.commit()

    def get(self, path: str, mtime_ns: int, size_bytes: int) -> dict[str, Any] | None:
        from contextlib import closing
        with self._lock, closing(self._connect()) as conn:
            row = conn.execute(
                """
                SELECT duration_ms, width, height, fps, video_codec, audio_codec, bitrate
                FROM media_meta
                WHERE path = ? AND mtime_ns = ? AND size_bytes = ?
                """,
                (path, mtime_ns, size_bytes),
            ).fetchone()
        if not row:
            return None
        return {
            "durationMs": row[0],
            "width": row[1],
            "height": row[2],
            "fps": row[3],
            "videoCodec": row[4],
            "audioCodec": row[5],
            "bitrate": row[6],
        }

    def put(self, path: str, mtime_ns: int, size_bytes: int, meta: dict[str, Any]) -> None:
        from contextlib import closing
        with self._lock, closing(self._connect()) as conn:
            conn.execute(
                """
                INSERT OR REPLACE INTO media_meta
                (path, mtime_ns, size_bytes, duration_ms, width, height, fps, video_codec, audio_codec, bitrate)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                (
                    path,
                    mtime_ns,
                    size_bytes,
                    int(meta.get("durationMs", 0)),
                    int(meta.get("width", 0)),
                    int(meta.get("height", 0)),
                    float(meta.get("fps", 0.0)),
                    str(meta.get("videoCodec", "")),
                    str(meta.get("audioCodec", "")),
                    int(meta.get("bitrate", 0)),
                ),
            )
            conn.commit()

    def invalidate(self, path: str) -> None:
        from contextlib import closing
        with self._lock, closing(self._connect()) as conn:
            conn.execute("DELETE FROM media_meta WHERE path = ?", (path,))
            conn.commit()


class DiskIndexer:
    """Fast background recursive directory size indexer with incremental updates."""

    SKIP_NAMES = {"$Recycle.Bin", "System Volume Information"}

    def __init__(self, roots_provider) -> None:
        self._roots_provider = roots_provider
        self._lock = threading.RLock()
        # path_lower -> { "size": int, "files": int, "dirs": int, "media": int }
        self._stats: dict[str, dict[str, int]] = {}
        self._scanning = False
        self._last_scan_finished = 0.0
        self._scan_generation = 0

    @staticmethod
    def _key(path: str) -> str:
        return os.path.abspath(path).rstrip("\\/").lower() or path.lower()

    def status(self) -> dict[str, Any]:
        with self._lock:
            return {
                "scanning": self._scanning,
                "indexedDirs": len(self._stats),
                "lastScanEpoch": int(self._last_scan_finished),
            }

    def get_dir_stats(self, path: str) -> dict[str, int] | None:
        with self._lock:
            val = self._stats.get(self._key(path))
            return dict(val) if val else None

    def start_background_scan(self, target_path: str | None = None) -> None:
        t = threading.Thread(
            target=self._run_scan,
            args=(target_path,),
            daemon=True,
            name="DeckVaultIndexer",
        )
        t.start()

    def _run_scan(self, target_path: str | None) -> None:
        with self._lock:
            self._scan_generation += 1
            my_gen = self._scan_generation
            self._scanning = True

        try:
            targets = [target_path] if target_path else self._roots_provider()
            for root in targets:
                if os.path.isdir(root):
                    self._scan_recursive(root, my_gen)
            with self._lock:
                if my_gen == self._scan_generation:
                    self._last_scan_finished = time.time()
        finally:
            with self._lock:
                if my_gen == self._scan_generation:
                    self._scanning = False

    def _scan_recursive(self, path: str, generation: int) -> tuple[int, int, int, int]:
        total_size = 0
        total_files = 0
        total_dirs = 0
        total_media = 0

        subdirs: list[str] = []
        try:
            with os.scandir(path) as it:
                for entry in it:
                    name = entry.name
                    if name in self.SKIP_NAMES:
                        continue
                    try:
                        if entry.is_symlink():
                            continue
                        if entry.is_dir(follow_symlinks=False):
                            subdirs.append(entry.path)
                            total_dirs += 1
                        elif entry.is_file(follow_symlinks=False):
                            st = entry.stat(follow_symlinks=False)
                            total_size += st.st_size
                            total_files += 1
                            if classify_media_type(name) in ("video", "image"):
                                total_media += 1
                    except OSError:
                        continue
        except OSError:
            return (0, 0, 0, 0)

        for sub in subdirs:
            s_size, s_files, s_dirs, s_media = self._scan_recursive(sub, generation)
            total_size += s_size
            total_files += s_files
            total_dirs += s_dirs
            total_media += s_media

        with self._lock:
            self._stats[self._key(path)] = {
                "size": total_size,
                "files": total_files,
                "dirs": total_dirs,
                "media": total_media,
            }
        return (total_size, total_files, total_dirs, total_media)

    def compute_immediate_if_missing(self, path: str) -> dict[str, int]:
        existing = self.get_dir_stats(path)
        if existing is not None:
            return existing
        s_size, s_files, s_dirs, s_media = self._scan_recursive(path, self._scan_generation)
        return {"size": s_size, "files": s_files, "dirs": s_dirs, "media": s_media}

    def adjust_ancestors(self, file_path: str, size_delta: int, file_delta: int = 0, media_delta: int = 0) -> None:
        """Immediately propagate size/file changes up the cached ancestor chain."""
        cur = os.path.dirname(os.path.abspath(file_path))
        with self._lock:
            while cur:
                k = self._key(cur)
                if k in self._stats:
                    entry = self._stats[k]
                    entry["size"] = max(0, entry["size"] + size_delta)
                    entry["files"] = max(0, entry["files"] + file_delta)
                    entry["media"] = max(0, entry["media"] + media_delta)
                parent = os.path.dirname(cur)
                if parent == cur:
                    break
                cur = parent


class MediaService:
    """FFmpeg/FFprobe-powered probing, thumbnailing, storyboards, and video trimming."""

    def __init__(self, cache: MetadataCache) -> None:
        self.cache = cache
        self.ffmpeg = find_binary("ffmpeg")
        self.ffprobe = find_binary("ffprobe")
        self._probe_pool = concurrent.futures.ThreadPoolExecutor(max_workers=4)
        self._render_locks: dict[str, threading.Lock] = {}
        self._locks_guard = threading.Lock()

    def _lock_for(self, key: str) -> threading.Lock:
        with self._locks_guard:
            if key not in self._render_locks:
                if len(self._render_locks) > 512:
                    self._render_locks.clear()
                self._render_locks[key] = threading.Lock()
            return self._render_locks[key]

    @staticmethod
    def _file_sig(path: str, st: os.stat_result) -> str:
        raw = f"{os.path.abspath(path).lower()}|{st.st_mtime_ns}|{st.st_size}".encode("utf-8")
        return hashlib.sha1(raw).hexdigest()

    def probe_video(self, path: str, st: os.stat_result | None = None) -> dict[str, Any]:
        if st is None:
            st = os.stat(path)
        cached = self.cache.get(path, st.st_mtime_ns, st.st_size)
        if cached is not None:
            return cached
        if not self.ffprobe:
            return {"durationMs": 0, "width": 0, "height": 0, "fps": 0.0, "videoCodec": "", "audioCodec": "", "bitrate": 0}

        cmd = [
            self.ffprobe, "-v", "quiet", "-print_format", "json",
            "-show_format", "-show_streams", path,
        ]
        try:
            out = subprocess.check_output(cmd, stderr=subprocess.DEVNULL, timeout=12, creationflags=SUBPROCESS_FLAGS)
            parsed = json.loads(out.decode("utf-8", errors="replace"))
        except Exception:
            return {"durationMs": 0, "width": 0, "height": 0, "fps": 0.0, "videoCodec": "", "audioCodec": "", "bitrate": 0}

        fmt = parsed.get("format", {})
        streams = parsed.get("streams", [])
        v_stream = next((s for s in streams if s.get("codec_type") == "video"), {})
        a_stream = next((s for s in streams if s.get("codec_type") == "audio"), {})

        dur_s = float(fmt.get("duration") or v_stream.get("duration") or 0.0)
        fps = 0.0
        rate_str = v_stream.get("r_frame_rate") or v_stream.get("avg_frame_rate") or "0/1"
        if "/" in str(rate_str):
            num, den = str(rate_str).split("/", 1)
            try:
                d = float(den)
                if d > 0:
                    fps = round(float(num) / d, 2)
            except ValueError:
                fps = 0.0

        meta = {
            "durationMs": int(dur_s * 1000),
            "width": int(v_stream.get("width") or 0),
            "height": int(v_stream.get("height") or 0),
            "fps": fps,
            "videoCodec": str(v_stream.get("codec_name") or ""),
            "audioCodec": str(a_stream.get("codec_name") or ""),
            "bitrate": int(fmt.get("bit_rate") or 0),
        }
        self.cache.put(path, st.st_mtime_ns, st.st_size, meta)
        return meta

    def invalidate_media_caches(self, path: str) -> None:
        self.cache.invalidate(path)

    def get_or_build_thumbnail(self, path: str, width: int = 480) -> Path:
        st = os.stat(path)
        sig = self._file_sig(path, st)
        width = max(120, min(1280, width))
        out_path = THUMBS_DIR / f"{sig}_w{width}.jpg"
        if out_path.exists() and out_path.stat().st_size > 0:
            return out_path

        if not self.ffmpeg:
            raise RuntimeError("ffmpeg not installed")

        with self._lock_for(f"thumb:{sig}:{width}"):
            if out_path.exists() and out_path.stat().st_size > 0:
                return out_path

            media_type = classify_media_type(path)
            tmp_out = out_path.with_suffix(".tmp.jpg")
            if media_type == "image":
                cmd = [
                    self.ffmpeg, "-y", "-i", path,
                    "-vf", f"scale='min({width},iw)':-2",
                    "-frames:v", "1", "-q:v", "4", str(tmp_out),
                ]
            else:
                meta = self.probe_video(path, st)
                dur_s = max(0.0, meta["durationMs"] / 1000.0)
                seek_s = min(dur_s * 0.12, 15.0) if dur_s > 1.5 else 0.0
                cmd = [
                    self.ffmpeg, "-y", "-ss", f"{seek_s:.3f}", "-i", path,
                    "-an", "-sn", "-frames:v", "1",
                    "-vf", f"scale='min({width},iw)':-2",
                    "-q:v", "4", str(tmp_out),
                ]
            subprocess.run(
                cmd, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL,
                check=True, timeout=25, creationflags=SUBPROCESS_FLAGS,
            )
            os.replace(tmp_out, out_path)
            return out_path

    def get_or_build_storyboard(self, path: str, cols: int = 4, rows: int = 4) -> tuple[Path, dict[str, Any]]:
        """Build a fast 4x4 (16-frame) scrub sprite sheet using keyframe seeking."""
        st = os.stat(path)
        sig = self._file_sig(path, st)
        cols = max(2, min(6, cols))
        rows = max(2, min(6, rows))
        total_frames = cols * rows
        cell_w = 320
        cell_h = 180

        out_path = STORYBOARDS_DIR / f"{sig}_{cols}x{rows}.jpg"
        meta = self.probe_video(path, st)
        info = {
            "cols": cols,
            "rows": rows,
            "frameCount": total_frames,
            "cellWidth": cell_w,
            "cellHeight": cell_h,
            "durationMs": meta["durationMs"],
        }
        if out_path.exists() and out_path.stat().st_size > 0:
            return out_path, info

        if not self.ffmpeg:
            raise RuntimeError("ffmpeg not installed")

        with self._lock_for(f"sb:{sig}:{cols}x{rows}"):
            if out_path.exists() and out_path.stat().st_size > 0:
                return out_path, info

            dur_s = max(0.5, meta["durationMs"] / 1000.0)
            tmp_out = out_path.with_suffix(".tmp.jpg")

            # Use multi-input fast keyframe seeking (-ss before -i) so a 4K 2-hour movie takes <1.5s!
            cmd: list[str] = [self.ffmpeg, "-y"]
            for i in range(total_frames):
                frac = (i + 0.5) / total_frames
                t_s = max(0.0, min(dur_s - 0.1, dur_s * frac))
                cmd.extend(["-ss", f"{t_s:.3f}", "-noaccurate_seek", "-i", path])

            filter_parts = []
            stack_inputs = []
            layout_entries = []
            for i in range(total_frames):
                r = i // cols
                c = i % cols
                filter_parts.append(
                    f"[{i}:v]scale={cell_w}:{cell_h}:force_original_aspect_ratio=decrease,"
                    f"pad={cell_w}:{cell_h}:(ow-iw)/2:(oh-ih)/2:black[v{i}]"
                )
                stack_inputs.append(f"[v{i}]")
                layout_entries.append(f"{c * cell_w}_{r * cell_h}")

            filter_complex = (
                ";".join(filter_parts)
                + ";"
                + "".join(stack_inputs)
                + f"xstack=inputs={total_frames}:layout="
                + "|".join(layout_entries)
                + "[out]"
            )

            cmd.extend([
                "-filter_complex", filter_complex,
                "-map", "[out]",
                "-frames:v", "1",
                "-q:v", "5",
                str(tmp_out),
            ])
            subprocess.run(
                cmd, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL,
                check=True, timeout=45, creationflags=SUBPROCESS_FLAGS,
            )
            os.replace(tmp_out, out_path)
            return out_path, info

    def extract_frame(self, path: str, ms: int, width: int = 640) -> Path:
        st = os.stat(path)
        sig = self._file_sig(path, st)
        bucket_ms = max(0, (ms // 250) * 250)
        width = max(160, min(1280, width))
        out_path = FRAMES_DIR / f"{sig}_{bucket_ms}_w{width}.jpg"
        if out_path.exists() and out_path.stat().st_size > 0:
            return out_path
        if not self.ffmpeg:
            raise RuntimeError("ffmpeg not installed")

        tmp_out = out_path.with_suffix(".tmp.jpg")
        seek_s = bucket_ms / 1000.0
        cmd = [
            self.ffmpeg, "-y", "-ss", f"{seek_s:.3f}", "-i", path,
            "-an", "-sn", "-frames:v", "1",
            "-vf", f"scale='min({width},iw)':-2",
            "-q:v", "4", str(tmp_out),
        ]
        subprocess.run(
            cmd, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL,
            check=True, timeout=20, creationflags=SUBPROCESS_FLAGS,
        )
        os.replace(tmp_out, out_path)
        return out_path

    @staticmethod
    def _parse_ffmpeg_time_s(key: str, val: str) -> float | None:
        if key == "out_time_us":
            try:
                v = float(val)
                return v / 1_000_000.0 if v >= 0 else None
            except ValueError:
                return None
        if key == "out_time":
            parts = val.strip().split(":")
            if len(parts) == 3:
                try:
                    return int(parts[0]) * 3600 + int(parts[1]) * 60 + float(parts[2])
                except ValueError:
                    return None
        return None

    def _run_ffmpeg_with_progress(
        self,
        cmd: list[str],
        total_dur_s: float = 0.0,
        on_progress: Any = None,
        proc_holder: dict[str, Any] | None = None,
        fail_label: str = "FFmpeg failed",
    ) -> None:
        """Run an FFmpeg command while streaming real-time progress % and supporting user cancel."""
        if proc_holder is not None and proc_holder.get("cancel"):
            raise InterruptedError("Cancelled by user")
        full_cmd = list(cmd[:-1]) + ["-progress", "pipe:1", "-nostats", cmd[-1]]
        proc = subprocess.Popen(
            full_cmd,
            stdout=subprocess.PIPE,
            stderr=subprocess.PIPE,
            text=True,
            encoding="utf-8",
            errors="replace",
            bufsize=1,
            creationflags=SUBPROCESS_FLAGS,
        )
        if proc_holder is not None:
            proc_holder["proc"] = proc

        stderr_chunks: list[str] = []

        def _drain_stderr() -> None:
            try:
                assert proc.stderr is not None
                for chunk in iter(lambda: proc.stderr.read(2048), ""):
                    stderr_chunks.append(chunk)
                    if sum(len(x) for x in stderr_chunks) > 8192:
                        stderr_chunks[:] = ["".join(stderr_chunks)[-4096:]]
            except Exception:
                pass

        err_t = threading.Thread(target=_drain_stderr, daemon=True)
        err_t.start()

        out_s = 0.0
        speed_txt = ""
        speed_num = 1.0
        last_emit = 0.0
        try:
            assert proc.stdout is not None
            for raw_line in proc.stdout:
                if proc_holder is not None and proc_holder.get("cancel"):
                    proc.terminate()
                    break
                line = raw_line.strip()
                if "=" not in line:
                    continue
                k, v = line.split("=", 1)
                t_s = self._parse_ffmpeg_time_s(k, v)
                if t_s is not None:
                    out_s = t_s
                elif k == "speed":
                    speed_txt = v.strip()
                    if speed_txt.endswith("x"):
                        try:
                            speed_num = max(0.05, float(speed_txt[:-1]))
                        except ValueError:
                            pass
                elif k == "progress" and on_progress is not None:
                    now = time.monotonic()
                    if v == "end" or (now - last_emit) >= 0.25:
                        last_emit = now
                        if v == "end":
                            pct = 99.0
                        elif total_dur_s > 0.05:
                            pct = max(1.0, min(98.5, (out_s / total_dur_s) * 100.0))
                        else:
                            pct = 50.0
                        rem_s = max(0.0, (total_dur_s - out_s) / speed_num) if total_dur_s > 0.05 else None
                        on_progress({
                            "progressPct": round(pct, 1),
                            "outSec": round(out_s, 1),
                            "totalSec": round(total_dur_s, 1),
                            "speed": speed_txt,
                            "remainingSec": round(rem_s, 1) if rem_s is not None else None,
                        })
            rc = proc.wait(timeout=3600)
        finally:
            err_t.join(timeout=2)
            if proc_holder is not None and proc_holder.get("proc") is proc:
                proc_holder["proc"] = None

        if proc_holder is not None and proc_holder.get("cancel"):
            raise InterruptedError("Cancelled by user")
        if rc != 0:
            err_txt = "".join(stderr_chunks)
            raise RuntimeError(err_txt[-600:] or fail_label)

    def trim_and_replace(
        self,
        path: str,
        start_ms: int,
        end_ms: int,
        mode: str = "lossless",
        crf: int = 23,
        recycle_original: bool = True,
        on_progress: Any = None,
        proc_holder: dict[str, Any] | None = None,
    ) -> dict[str, Any]:
        if not self.ffmpeg or not self.ffprobe:
            raise RuntimeError("ffmpeg and ffprobe are required for trimming")
        if not os.path.isfile(path):
            raise FileNotFoundError(path)

        old_stat = os.stat(path)
        old_size = old_stat.st_size
        meta = self.probe_video(path, old_stat)
        total_ms = meta.get("durationMs", 0)
        if total_ms > 0:
            end_ms = min(end_ms, total_ms)
        if start_ms < 0 or end_ms <= start_ms + 200:
            raise ValueError("Trim range must be at least 200ms")

        start_s = start_ms / 1000.0
        dur_s = (end_ms - start_ms) / 1000.0
        parent = os.path.dirname(path)
        ext = os.path.splitext(path)[1]
        tmp_path = os.path.join(parent, f".deckvault_trim_{uuid.uuid4().hex}{ext}")

        try:
            if mode == "compress":
                crf = max(16, min(32, int(crf)))
                cmd = [
                    self.ffmpeg, "-y",
                    "-ss", f"{start_s:.3f}",
                    "-i", path,
                    "-t", f"{dur_s:.3f}",
                    "-map", "0:v:0", "-map", "0:a?",
                    "-c:v", "libx264", "-preset", "veryfast", "-crf", str(crf),
                    "-c:a", "aac", "-b:a", "192k",
                    "-movflags", "+faststart",
                    tmp_path,
                ]
                self._run_ffmpeg_with_progress(cmd, dur_s, on_progress, proc_holder, "ffmpeg compress trim failed")
            else:
                cmd_full = [
                    self.ffmpeg, "-y",
                    "-ss", f"{start_s:.3f}",
                    "-i", path,
                    "-t", f"{dur_s:.3f}",
                    "-map", "0", "-c", "copy",
                    "-avoid_negative_ts", "make_zero",
                    tmp_path,
                ]
                try:
                    self._run_ffmpeg_with_progress(cmd_full, dur_s, on_progress, proc_holder, "ffmpeg lossless trim failed")
                except InterruptedError:
                    raise
                except Exception:
                    cmd_safe = [
                        self.ffmpeg, "-y",
                        "-ss", f"{start_s:.3f}",
                        "-i", path,
                        "-t", f"{dur_s:.3f}",
                        "-map", "0:v?", "-map", "0:a?", "-map", "0:s?",
                        "-c", "copy",
                        "-avoid_negative_ts", "make_zero",
                        tmp_path,
                    ]
                    self._run_ffmpeg_with_progress(cmd_safe, dur_s, on_progress, proc_holder, "ffmpeg lossless trim failed")

            if not os.path.isfile(tmp_path) or os.path.getsize(tmp_path) < 1024:
                raise RuntimeError("Trim output was empty or invalid")

            new_stat = os.stat(tmp_path)
            new_size = new_stat.st_size
            new_meta = self.probe_video(tmp_path, new_stat)
            if new_meta.get("durationMs", 0) <= 0:
                raise RuntimeError("Trim output failed ffprobe validation")

            if recycle_original:
                send_to_recycle_bin(path)
                os.replace(tmp_path, path)
            else:
                os.replace(tmp_path, path)

            self.invalidate_media_caches(path)
            final_stat = os.stat(path)
            self.cache.put(path, final_stat.st_mtime_ns, final_stat.st_size, new_meta)
            saved_bytes = max(0, old_size - new_size)
            return {
                "ok": True,
                "path": path,
                "oldSizeBytes": old_size,
                "newSizeBytes": new_size,
                "savedBytes": saved_bytes,
                "sizeDeltaBytes": new_size - old_size,
                "newDurationMs": new_meta.get("durationMs", 0),
                "recycledOriginal": recycle_original,
            }
        except Exception:
            if os.path.exists(tmp_path):
                try:
                    os.remove(tmp_path)
                except OSError:
                    pass
            raise

    def edit_video(
        self,
        path: str,
        start_ms: int = 0,
        end_ms: int = 0,
        crop: dict[str, Any] | None = None,
        rotate: int = 0,
        flip_h: bool = False,
        flip_v: bool = False,
        speed: float = 1.0,
        mute_audio: bool = False,
        mode: str = "lossless",
        crf: int = 22,
        save_mode: str = "overwrite",
        recycle_original: bool = True,
        brightness: float = 0.0,
        contrast: float = 1.0,
        saturation: float = 1.0,
        sharpen: float = 0.0,
        deband: bool = False,
        stabilize: bool = False,
        fade_sec: float = 0.0,
        normalize_audio: bool = False,
        on_progress: Any = None,
        proc_holder: dict[str, Any] | None = None,
    ) -> dict[str, Any]:
        """Full video studio pipeline: cut, crop, rotate/flip, speed, color, deband, stabilize, fade, normalize audio."""
        if not self.ffmpeg or not self.ffprobe:
            raise RuntimeError("ffmpeg and ffprobe are required for editing")
        if not os.path.isfile(path):
            raise FileNotFoundError(path)

        old_stat = os.stat(path)
        old_size = old_stat.st_size
        meta = self.probe_video(path, old_stat)
        has_audio = bool(meta.get("audioCodec"))
        total_ms = meta.get("durationMs", 0)
        if total_ms > 0:
            end_ms = min(end_ms, total_ms) if end_ms > 0 else total_ms
        if start_ms < 0 or end_ms <= start_ms + 200:
            raise ValueError("Selected duration must be at least 200ms")

        has_crop = False
        crop_filter = ""
        if crop and isinstance(crop, dict):
            x_f = max(0.0, min(0.95, float(crop.get("x", 0.0))))
            y_f = max(0.0, min(0.95, float(crop.get("y", 0.0))))
            w_f = max(0.05, min(1.0 - x_f, float(crop.get("w", 1.0))))
            h_f = max(0.05, min(1.0 - y_f, float(crop.get("h", 1.0))))
            if x_f > 0.005 or y_f > 0.005 or w_f < 0.995 or h_f < 0.995:
                has_crop = True
                crop_filter = (
                    f"crop=max(2\\,trunc(iw*{w_f:.5f}/2)*2):max(2\\,trunc(ih*{h_f:.5f}/2)*2):"
                    f"min(iw-2\\,trunc(iw*{x_f:.5f}/2)*2):min(ih-2\\,trunc(ih*{y_f:.5f}/2)*2)"
                )

        rot = int(rotate) % 360
        spd = max(0.25, min(4.0, float(speed)))
        bri = max(-0.5, min(0.5, float(brightness)))
        con = max(0.5, min(2.0, float(contrast)))
        sat = max(0.0, min(2.5, float(saturation)))
        shrp = max(0.0, min(1.0, float(sharpen)))
        f_sec = max(0.0, min(5.0, float(fade_sec)))
        has_color = abs(bri) > 0.01 or abs(con - 1.0) > 0.02 or abs(sat - 1.0) > 0.02

        needs_reencode = (
            mode == "compress"
            or has_crop
            or rot != 0
            or bool(flip_h)
            or bool(flip_v)
            or abs(spd - 1.0) > 0.02
            or bool(mute_audio)
            or has_color
            or shrp > 0.05
            or bool(deband)
            or bool(stabilize)
            or f_sec > 0.05
            or bool(normalize_audio)
        )

        if not needs_reencode and save_mode == "overwrite":
            return self.trim_and_replace(path, start_ms, end_ms, mode, crf, recycle_original, on_progress, proc_holder)

        start_s = start_ms / 1000.0
        dur_s = (end_ms - start_ms) / 1000.0
        out_dur_s = dur_s / spd
        parent = os.path.dirname(path)
        ext = os.path.splitext(os.path.basename(path))[1]
        out_ext = ext if ext.lower() in (".mp4", ".mov", ".mkv", ".m4v") else ".mp4"
        tmp_path = os.path.join(parent, f".deckvault_edit_{uuid.uuid4().hex}{out_ext}")

        try:
            if not needs_reencode:
                cmd = [
                    self.ffmpeg, "-y",
                    "-ss", f"{start_s:.3f}",
                    "-i", path,
                    "-t", f"{dur_s:.3f}",
                    "-map", "0:v?", "-map", "0:a?", "-map", "0:s?",
                    "-c", "copy",
                    "-avoid_negative_ts", "make_zero",
                    tmp_path,
                ]
                self._run_ffmpeg_with_progress(cmd, dur_s, on_progress, proc_holder, "ffmpeg edit failed")
            else:
                vf_parts: list[str] = []
                if rot == 90:
                    vf_parts.append("transpose=1")
                elif rot == 180:
                    vf_parts.append("transpose=1,transpose=1")
                elif rot == 270:
                    vf_parts.append("transpose=2")
                if flip_h:
                    vf_parts.append("hflip")
                if flip_v:
                    vf_parts.append("vflip")
                if crop_filter:
                    vf_parts.append(crop_filter)
                if has_crop or rot != 0:
                    vf_parts.append("setsar=1")
                if stabilize:
                    vf_parts.append("deshake")
                if deband:
                    vf_parts.append("deband")
                if has_color:
                    vf_parts.append(f"eq=brightness={bri:.3f}:contrast={con:.3f}:saturation={sat:.3f}")
                if shrp > 0.05:
                    vf_parts.append(f"cas=strength={shrp:.2f}")
                if f_sec > 0.05 and dur_s > f_sec * 2.1:
                    vf_parts.append(f"fade=t=in:st=0:d={f_sec:.2f}")
                    vf_parts.append(f"fade=t=out:st={max(0.0, dur_s - f_sec):.3f}:d={f_sec:.2f}")
                vf_parts.append("scale=trunc(iw/2)*2:trunc(ih/2)*2")
                if abs(spd - 1.0) > 0.02:
                    vf_parts.append(f"setpts={1.0 / spd:.5f}*PTS")

                af_parts: list[str] = []
                if has_audio and not mute_audio:
                    if normalize_audio:
                        af_parts.append("loudnorm=I=-16:TP=-1.5:LRA=11")
                    if f_sec > 0.05 and dur_s > f_sec * 2.1:
                        af_parts.append(f"afade=t=in:st=0:d={f_sec:.2f}")
                        af_parts.append(f"afade=t=out:st={max(0.0, dur_s - f_sec):.3f}:d={f_sec:.2f}")
                    if abs(spd - 1.0) > 0.02:
                        rem = spd
                        while rem > 2.0:
                            af_parts.append("atempo=2.0")
                            rem /= 2.0
                        while rem < 0.5:
                            af_parts.append("atempo=0.5")
                            rem /= 0.5
                        af_parts.append(f"atempo={rem:.4f}")

                crf = max(16, min(32, int(crf)))
                cmd = [
                    self.ffmpeg, "-y",
                    "-ss", f"{start_s:.3f}",
                    "-i", path,
                    "-t", f"{dur_s:.3f}",
                    "-map", "0:v:0",
                ]
                if mute_audio or not has_audio:
                    cmd.append("-an")
                else:
                    cmd.extend(["-map", "0:a?"])
                if vf_parts:
                    cmd.extend(["-vf", ",".join(vf_parts)])
                if af_parts and has_audio and not mute_audio:
                    cmd.extend(["-af", ",".join(af_parts)])
                cmd.extend([
                    "-c:v", "libx264", "-preset", "veryfast", "-crf", str(crf),
                    "-pix_fmt", "yuv420p",
                ])
                if has_audio and not mute_audio:
                    cmd.extend(["-c:a", "aac", "-b:a", "192k"])
                if out_ext.lower() != ".mkv":
                    cmd.extend(["-movflags", "+faststart"])
                cmd.append(tmp_path)
                self._run_ffmpeg_with_progress(cmd, out_dur_s, on_progress, proc_holder, "ffmpeg edit failed")

            if not os.path.isfile(tmp_path) or os.path.getsize(tmp_path) < 1024:
                raise RuntimeError("Edited output was empty or invalid")

            new_meta = self.probe_video(tmp_path)
            res = self._atomic_finish_media(tmp_path, path, "_edited", out_ext, save_mode, recycle_original, old_size)
            self.invalidate_media_caches(res["path"])
            final_stat = os.stat(res["path"])
            self.cache.put(res["path"], final_stat.st_mtime_ns, final_stat.st_size, new_meta)
            res["newDurationMs"] = new_meta.get("durationMs", 0)
            return res
        except Exception:
            if os.path.exists(tmp_path):
                try:
                    os.remove(tmp_path)
                except OSError:
                    pass
            raise

    def save_snapshot(self, path: str, ms: int) -> str:
        """Save a full-resolution PNG/JPG frame capture next to the video."""
        if not self.ffmpeg:
            raise RuntimeError("ffmpeg not available")
        if not os.path.isfile(path):
            raise FileNotFoundError(path)
        parent = os.path.dirname(path)
        stem = os.path.splitext(os.path.basename(path))[0]
        sec = max(0.0, ms / 1000.0)
        dest = _resolve_unique_dest(parent, f"{stem}_frame_{int(ms)}ms.png", "rename")
        assert dest is not None
        cmd = [
            self.ffmpeg, "-y",
            "-ss", f"{sec:.3f}",
            "-i", path,
            "-frames:v", "1",
            "-q:v", "2",
            dest,
        ]
        proc = subprocess.run(cmd, capture_output=True, text=True, timeout=60, creationflags=SUBPROCESS_FLAGS)
        if proc.returncode != 0 or not os.path.isfile(dest):
            raise RuntimeError("Snapshot extraction failed")
        return dest

    def has_encoder(self, encoder_name: str) -> bool:
        """Check whether the local ffmpeg binary supports a specific encoder."""
        if not self.ffmpeg:
            return False
        cache_attr = "_enc_cache"
        enc_map = getattr(self, cache_attr, None)
        if enc_map is None:
            enc_map = {}
            try:
                out = subprocess.check_output(
                    [self.ffmpeg, "-hide_banner", "-encoders"],
                    stderr=subprocess.DEVNULL, timeout=8, creationflags=SUBPROCESS_FLAGS,
                ).decode("utf-8", errors="replace")
                for key in ("libx264", "libx265", "libsvtav1"):
                    enc_map[key] = (key in out)
            except Exception:
                enc_map = {"libx264": True, "libx265": False}
            setattr(self, cache_attr, enc_map)
        return bool(enc_map.get(encoder_name, False))

    @staticmethod
    def _atomic_finish_media(
        tmp_path: str,
        orig_path: str,
        suffix: str,
        out_ext: str,
        save_mode: str,
        recycle_original: bool,
        old_size: int,
    ) -> dict[str, Any]:
        new_size = os.path.getsize(tmp_path)
        parent = os.path.dirname(orig_path)
        stem = os.path.splitext(os.path.basename(orig_path))[0]
        if save_mode == "copy":
            dest_path = _resolve_unique_dest(parent, f"{stem}{suffix}{out_ext}", "rename") or orig_path
            os.replace(tmp_path, dest_path)
            return {
                "ok": True,
                "path": dest_path,
                "name": os.path.basename(dest_path),
                "oldSizeBytes": old_size,
                "newSizeBytes": new_size,
                "savedBytes": 0,
                "sizeDeltaBytes": new_size,
                "saveMode": "copy",
            }
        last_err: Exception | None = None
        for attempt in range(3):
            try:
                if recycle_original:
                    send_to_recycle_bin(orig_path)
                os.replace(tmp_path, orig_path)
                last_err = None
                break
            except Exception as exc:
                last_err = exc
                if attempt < 2:
                    gc.collect()
                    time.sleep(0.15)
        if last_err is not None:
            raise last_err
        return {
            "ok": True,
            "path": orig_path,
            "name": os.path.basename(orig_path),
            "oldSizeBytes": old_size,
            "newSizeBytes": new_size,
            "savedBytes": max(0, old_size - new_size),
            "sizeDeltaBytes": new_size - old_size,
            "saveMode": "overwrite",
        }

    def estimate_compression(
        self,
        path: str,
        target_height: int = 0,
        crf: int = 24,
        codec: str = "h264",
        audio_kbps: int = 128,
        target_mb: float = 0.0,
        sample_probe: bool = False,
        quality: int = 82,
    ) -> dict[str, Any]:
        """Predict compressed output size (analytical, exact target MB, or fast 1.5s sample encode)."""
        if not os.path.isfile(path):
            raise FileNotFoundError(path)
        st = os.stat(path)
        old_size = st.st_size
        mtype = classify_media_type(path)

        if mtype == "image":
            from PIL import Image
            with Image.open(path) as im:
                ow, oh = im.size
                scale = (target_height / oh) if (target_height > 0 and target_height < oh) else 1.0
                nw = max(2, int(round(ow * scale)))
                nh = max(2, int(round(oh * scale)))
                if scale < 1.0:
                    im_resized = im.resize((nw, nh), Image.Resampling.LANCZOS)
                else:
                    im_resized = im
                buf = io.BytesIO()
                ext_l = os.path.splitext(path)[1].lower()
                fmt_name = "WEBP" if ext_l == ".webp" else "JPEG"
                save_im = im_resized.convert("RGB") if fmt_name == "JPEG" else im_resized
                save_im.save(buf, format=fmt_name, quality=max(30, min(98, int(quality))), optimize=True)
                est_bytes = buf.tell()
                return {
                    "mediaType": "image",
                    "oldSizeBytes": old_size,
                    "estimatedBytes": est_bytes,
                    "oldWidth": ow,
                    "oldHeight": oh,
                    "newWidth": nw,
                    "newHeight": nh,
                    "method": "exact_preview",
                    "encoderUsed": fmt_name.lower(),
                }

        meta = self.probe_video(path, st)
        dur_s = max(0.5, meta.get("durationMs", 0) / 1000.0)
        ow = int(meta.get("width", 0) or 1920)
        oh = int(meta.get("height", 0) or 1080)
        scale = (target_height / oh) if (target_height > 0 and target_height < oh) else 1.0
        nw = max(2, int(round(ow * scale)) // 2 * 2)
        nh = max(2, int(round(oh * scale)) // 2 * 2)

        use_hevc = (codec.lower() in ("h265", "hevc")) and self.has_encoder("libx265")
        v_enc = "libx265" if use_hevc else "libx264"

        if target_mb > 0:
            est_bytes = max(32 * 1024, int(target_mb * 1024 * 1024))
            return {
                "mediaType": "video",
                "oldSizeBytes": old_size,
                "estimatedBytes": est_bytes,
                "oldWidth": ow,
                "oldHeight": oh,
                "newWidth": nw,
                "newHeight": nh,
                "durationMs": meta.get("durationMs", 0),
                "method": "target_size",
                "encoderUsed": v_enc,
            }

        method = "analytical"
        est_bytes: int | None = None
        if sample_probe and self.ffmpeg and dur_s >= 2.0:
            tmp_probe = os.path.join(
                os.path.dirname(path), f".deckvault_probe_{uuid.uuid4().hex}.mp4"
            )
            try:
                sample_dur = min(1.5, max(0.8, dur_s * 0.25))
                sample_start = max(0.0, (dur_s - sample_dur) * 0.5)
                cmd = [
                    self.ffmpeg, "-y", "-ss", f"{sample_start:.2f}", "-i", path,
                    "-t", f"{sample_dur:.2f}", "-map", "0:v:0",
                ]
                if audio_kbps > 0:
                    cmd.extend(["-map", "0:a?"])
                else:
                    cmd.append("-an")
                if target_height > 0 and target_height < oh:
                    cmd.extend(["-vf", f"scale=-2:{target_height}:flags=lanczos,setsar=1"])
                else:
                    cmd.extend(["-vf", "scale=trunc(iw/2)*2:trunc(ih/2)*2,setsar=1"])
                crf_clamped = max(16, min(36, int(crf)))
                cmd.extend(["-c:v", v_enc, "-preset", "veryfast", "-crf", str(crf_clamped), "-pix_fmt", "yuv420p"])
                if audio_kbps > 0:
                    cmd.extend(["-c:a", "aac", "-b:a", f"{int(audio_kbps)}k"])
                cmd.append(tmp_probe)
                proc = subprocess.run(cmd, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL, timeout=45, creationflags=SUBPROCESS_FLAGS)
                if proc.returncode == 0 and os.path.isfile(tmp_probe) and os.path.getsize(tmp_probe) > 2048:
                    sample_size = os.path.getsize(tmp_probe)
                    est_bytes = max(32 * 1024, int((sample_size / sample_dur) * dur_s * 1.03))
                    method = "sample_probe"
            except Exception:
                est_bytes = None
            finally:
                if os.path.exists(tmp_probe):
                    try:
                        os.remove(tmp_probe)
                    except OSError:
                        pass

        if est_bytes is None:
            pix_ratio = (nw * nh) / max(1, ow * oh)
            # Baseline H.264 bitrate around 2.8 Mbps at 1080p CRF 23 scaled by resolution & CRF
            crf_factor = 2.0 ** ((23.0 - float(crf)) / 6.0)
            codec_factor = 0.66 if use_hevc else 1.0
            src_bps = int(meta.get("bitrate", 0) or (old_size * 8 / dur_s))
            base_v_bps = min(max(src_bps * 0.85, 400_000), max(3_500_000 * pix_ratio, src_bps * 0.92))
            est_v_bps = max(160_000, base_v_bps * (pix_ratio ** 0.82) * crf_factor * codec_factor)
            est_a_bps = max(0, int(audio_kbps)) * 1000
            est_bytes = max(32 * 1024, int(((est_v_bps + est_a_bps) / 8.0) * dur_s))

        return {
            "mediaType": "video",
            "oldSizeBytes": old_size,
            "estimatedBytes": est_bytes,
            "oldWidth": ow,
            "oldHeight": oh,
            "newWidth": nw,
            "newHeight": nh,
            "durationMs": meta.get("durationMs", 0),
            "method": method,
            "encoderUsed": v_enc,
        }

    def compress_media(
        self,
        path: str,
        target_height: int = 0,
        crf: int = 24,
        codec: str = "h264",
        audio_kbps: int = 128,
        target_mb: float = 0.0,
        save_mode: str = "copy",
        recycle_original: bool = True,
        quality: int = 82,
        on_progress: Any = None,
        proc_holder: dict[str, Any] | None = None,
    ) -> dict[str, Any]:
        """Compress an image or video cleanly with optional target size MB or CRF quality."""
        if not os.path.isfile(path):
            raise FileNotFoundError(path)
        old_stat = os.stat(path)
        old_size = old_stat.st_size
        parent = os.path.dirname(path)
        mtype = classify_media_type(path)
        ext = os.path.splitext(path)[1].lower()

        if mtype == "image":
            from PIL import Image
            if on_progress:
                on_progress({"progressPct": 25.0, "speed": "", "remainingSec": None})
            out_ext = ext if ext in (".jpg", ".jpeg", ".webp", ".png") else ".jpg"
            tmp_path = os.path.join(parent, f".deckvault_cmp_{uuid.uuid4().hex}{out_ext}")
            try:
                with Image.open(path) as im:
                    ow, oh = im.size
                    if target_height > 0 and target_height < oh:
                        nw = max(2, int(round(ow * (target_height / oh))))
                        im = im.resize((nw, int(target_height)), Image.Resampling.LANCZOS)
                    q = max(30, min(98, int(quality)))
                    if out_ext in (".jpg", ".jpeg"):
                        im.convert("RGB").save(tmp_path, format="JPEG", quality=q, optimize=True)
                    elif out_ext == ".webp":
                        im.save(tmp_path, format="WEBP", quality=q, method=4)
                    else:
                        im.save(tmp_path, format="PNG", optimize=True)
                res = self._atomic_finish_media(tmp_path, path, "_compressed", out_ext, save_mode, recycle_original, old_size)
                self.invalidate_media_caches(path)
                return res
            except Exception:
                if os.path.exists(tmp_path):
                    try:
                        os.remove(tmp_path)
                    except OSError:
                        pass
                raise

        if not self.ffmpeg or not self.ffprobe:
            raise RuntimeError("FFmpeg is required for video compression")
        meta = self.probe_video(path, old_stat)
        dur_s = max(0.5, meta.get("durationMs", 0) / 1000.0)
        oh = int(meta.get("height", 0) or 1080)
        has_audio = bool(meta.get("audioCodec"))
        use_hevc = (codec.lower() in ("h265", "hevc")) and self.has_encoder("libx265")
        v_enc = "libx265" if use_hevc else "libx264"
        out_ext = ".mp4" if save_mode == "copy" else (ext if ext in (".mp4", ".mov", ".mkv", ".m4v") else ".mp4")
        tmp_path = os.path.join(parent, f".deckvault_cmp_{uuid.uuid4().hex}{out_ext}")

        try:
            cmd = [self.ffmpeg, "-y", "-i", path, "-map", "0:v:0"]
            if audio_kbps <= 0 or not has_audio:
                cmd.append("-an")
            else:
                cmd.extend(["-map", "0:a?"])
            if target_height > 0 and target_height < oh:
                cmd.extend(["-vf", f"scale=-2:{int(target_height)}:flags=lanczos,setsar=1"])
            else:
                cmd.extend(["-vf", "scale=trunc(iw/2)*2:trunc(ih/2)*2,setsar=1"])
            cmd.extend(["-c:v", v_enc, "-preset", "medium", "-pix_fmt", "yuv420p"])
            if use_hevc:
                cmd.extend(["-tag:v", "hvc1"])
            if target_mb > 0:
                total_bps = (float(target_mb) * 1024 * 1024 * 8.0) / dur_s
                a_bps = max(0, int(audio_kbps)) * 1000 if has_audio else 0
                v_bps = max(120_000, int((total_bps - a_bps) * 0.94))
                v_kbps = max(120, v_bps // 1000)
                cmd.extend(["-b:v", f"{v_kbps}k", "-maxrate", f"{int(v_kbps * 1.35)}k", "-bufsize", f"{v_kbps * 2}k"])
            else:
                cmd.extend(["-crf", str(max(16, min(36, int(crf))))])
            if audio_kbps > 0 and has_audio:
                cmd.extend(["-c:a", "aac", "-b:a", f"{int(audio_kbps)}k"])
            if out_ext != ".mkv":
                cmd.extend(["-movflags", "+faststart"])
            cmd.append(tmp_path)

            self._run_ffmpeg_with_progress(cmd, dur_s, on_progress, proc_holder, "FFmpeg compression failed")
            if not os.path.isfile(tmp_path) or os.path.getsize(tmp_path) < 1024:
                raise RuntimeError("Compression output was empty or invalid")
            res = self._atomic_finish_media(tmp_path, path, "_compressed", out_ext, save_mode, recycle_original, old_size)
            self.invalidate_media_caches(path)
            st_new = os.stat(res["path"])
            self.cache.put(res["path"], st_new.st_mtime_ns, st_new.st_size, self.probe_video(res["path"], st_new))
            return res
        except Exception:
            if os.path.exists(tmp_path):
                try:
                    os.remove(tmp_path)
                except OSError:
                    pass
            raise

    def upscale_media(
        self,
        path: str,
        preset: str = "2x",
        sharpen: float = 0.9,
        denoise: bool = False,
        fps60: bool = False,
        crf: int = 19,
        save_mode: str = "copy",
        recycle_original: bool = True,
        mode: str = "cas",
        denoise_mode: str = "off",
        deband: bool = False,
        stabilize: bool = False,
        on_progress: Any = None,
        proc_holder: dict[str, Any] | None = None,
    ) -> dict[str, Any]:
        """Upscale and detail-enhance a video or image cleanly (CAS, Anime, Restore + Optional 60 FPS / Stabilize)."""
        if not os.path.isfile(path):
            raise FileNotFoundError(path)
        old_stat = os.stat(path)
        old_size = old_stat.st_size
        parent = os.path.dirname(path)
        mtype = classify_media_type(path)
        ext = os.path.splitext(path)[1].lower()
        sharp_v = max(0.0, min(2.0, float(sharpen)))
        up_mode = str(mode or "cas").lower()
        dn_mode = str(denoise_mode or ("fast" if denoise else "off")).lower()
        if denoise and dn_mode == "off":
            dn_mode = "fast"

        if mtype == "image":
            from PIL import Image, ImageFilter
            if on_progress:
                on_progress({"progressPct": 25.0, "speed": "", "remainingSec": None})
            out_ext = ext if ext in (".png", ".jpg", ".jpeg", ".webp") else ".png"
            tmp_path = os.path.join(parent, f".deckvault_up_{uuid.uuid4().hex}{out_ext}")
            try:
                with Image.open(path) as im:
                    work = im
                    if dn_mode != "off" or up_mode == "restore":
                        work = work.filter(ImageFilter.MedianFilter(size=3))
                    ow, oh = work.size
                    target_map = {"4k": 3840, "1440p": 2560, "1080p": 1920}
                    if preset in target_map:
                        tw = target_map[preset]
                        nw = max(ow, tw)
                        nh = int(round(oh * (nw / max(1, ow))))
                    else:
                        nw, nh = ow * 2, oh * 2
                    up = work.resize((nw, nh), Image.Resampling.LANCZOS)
                    if sharp_v > 0.05:
                        up = up.filter(ImageFilter.UnsharpMask(radius=2.0, percent=int(sharp_v * 120), threshold=2))
                    if out_ext in (".jpg", ".jpeg"):
                        up.convert("RGB").save(tmp_path, format="JPEG", quality=95, optimize=True)
                    elif out_ext == ".webp":
                        up.save(tmp_path, format="WEBP", quality=94, method=4)
                    else:
                        up.save(tmp_path, format="PNG", optimize=True)
                res = self._atomic_finish_media(tmp_path, path, f"_upscaled_{preset}", out_ext, save_mode, recycle_original, old_size)
                self.invalidate_media_caches(path)
                return res
            except Exception:
                if os.path.exists(tmp_path):
                    try:
                        os.remove(tmp_path)
                    except OSError:
                        pass
                raise

        if not self.ffmpeg or not self.ffprobe:
            raise RuntimeError("FFmpeg is required for video upscaling")
        meta = self.probe_video(path, old_stat)
        dur_s = max(0.5, meta.get("durationMs", 0) / 1000.0)
        has_audio = bool(meta.get("audioCodec"))
        ow = int(meta.get("width", 0) or 1280)
        oh = int(meta.get("height", 0) or 720)
        target_long = {"4k": 3840, "1440p": 2560, "1080p": 1920}.get(preset, 0)
        if target_long > 0:
            if ow >= oh:
                nw_f = float(target_long)
                nh_f = oh * (target_long / max(1, ow))
            else:
                nh_f = float(target_long)
                nw_f = ow * (target_long / max(1, oh))
        else:
            scale_f = min(2.0, 3840.0 / max(1, ow, oh))
            nw_f = ow * scale_f
            nh_f = oh * scale_f
        nw = max(2, min(3840, (int(round(nw_f)) // 2) * 2))
        nh = max(2, min(3840, (int(round(nh_f)) // 2) * 2))

        out_ext = ".mp4" if save_mode == "copy" else (ext if ext in (".mp4", ".mov", ".mkv", ".m4v") else ".mp4")
        tmp_path = os.path.join(parent, f".deckvault_up_{uuid.uuid4().hex}{out_ext}")
        try:
            vf_parts: list[str] = []
            if stabilize:
                vf_parts.append("deshake")
            if deband or up_mode == "restore":
                vf_parts.append("deband")
            if dn_mode == "hq":
                vf_parts.append("nlmeans=s=3.5:p=5:r=9")
            elif dn_mode == "fast" or (up_mode in ("anime", "restore") and dn_mode == "off"):
                vf_parts.append("hqdn3d=2.0:1.5:3.0:2.5" if up_mode == "restore" else "hqdn3d=1.6:1.2:2.4:1.8")

            vf_parts.append(f"scale={nw}:{nh}:flags=lanczos")

            if sharp_v > 0.05:
                cas_s = max(0.05, min(1.0, sharp_v * 0.72))
                vf_parts.append(f"cas=strength={cas_s:.2f}")
            if fps60:
                vf_parts.append("framerate=fps=60")
            vf_parts.append("setsar=1")

            cmd = [self.ffmpeg, "-y", "-i", path, "-map", "0:v:0"]
            if has_audio:
                cmd.extend(["-map", "0:a?"])
            else:
                cmd.append("-an")
            cmd.extend([
                "-vf", ",".join(vf_parts),
                "-c:v", "libx264", "-preset", "fast",
                "-crf", str(max(14, min(28, int(crf)))),
                "-pix_fmt", "yuv420p",
            ])
            if has_audio:
                cmd.extend(["-c:a", "aac", "-b:a", "192k"])
            if out_ext != ".mkv":
                cmd.extend(["-movflags", "+faststart"])
            cmd.append(tmp_path)

            self._run_ffmpeg_with_progress(cmd, dur_s, on_progress, proc_holder, "FFmpeg upscale failed")
            if not os.path.isfile(tmp_path) or os.path.getsize(tmp_path) < 1024:
                raise RuntimeError("Upscale output was empty or invalid")
            res = self._atomic_finish_media(tmp_path, path, f"_upscaled_{preset}", out_ext, save_mode, recycle_original, old_size)
            self.invalidate_media_caches(path)
            st_new = os.stat(res["path"])
            self.cache.put(res["path"], st_new.st_mtime_ns, st_new.st_size, self.probe_video(res["path"], st_new))
            return res
        except Exception:
            if os.path.exists(tmp_path):
                try:
                    os.remove(tmp_path)
                except OSError:
                    pass
            raise

    def extract_audio(
        self,
        path: str,
        fmt: str = "m4a",
        bitrate_kbps: int = 192,
        normalize: bool = False,
        start_ms: int = 0,
        end_ms: int = 0,
        on_progress: Any = None,
        proc_holder: dict[str, Any] | None = None,
    ) -> dict[str, Any]:
        """Extract audio track (MP3 or M4A) with optional trim range and loudness normalization."""
        if not self.ffmpeg or not self.ffprobe:
            raise RuntimeError("FFmpeg is required for audio extraction")
        if not os.path.isfile(path):
            raise FileNotFoundError(path)
        old_stat = os.stat(path)
        meta = self.probe_video(path, old_stat)
        if not meta.get("audioCodec"):
            raise ValueError("Source video has no audio stream")
        total_ms = int(meta.get("durationMs", 0) or 0)
        s_ms = max(0, int(start_ms))
        e_ms = int(end_ms) if int(end_ms) > s_ms + 200 else total_ms
        if total_ms > 0 and e_ms > 0:
            e_ms = min(e_ms, total_ms)
        dur_s = max(0.5, ((e_ms - s_ms) / 1000.0) if e_ms > s_ms else (total_ms / 1000.0))

        fmt_clean = str(fmt or "m4a").lower()
        if fmt_clean == "mp3" and self.has_encoder("libmp3lame"):
            out_ext, codec_name = ".mp3", "libmp3lame"
        else:
            out_ext, codec_name = ".m4a", "aac"

        parent = os.path.dirname(path)
        stem = os.path.splitext(os.path.basename(path))[0]
        tmp_path = os.path.join(parent, f".deckvault_aud_{uuid.uuid4().hex}{out_ext}")
        try:
            cmd = [self.ffmpeg, "-y"]
            if s_ms > 0:
                cmd.extend(["-ss", f"{s_ms / 1000.0:.3f}"])
            cmd.extend(["-i", path])
            if e_ms > s_ms and (s_ms > 0 or (total_ms > 0 and e_ms < total_ms - 100)):
                cmd.extend(["-t", f"{(e_ms - s_ms) / 1000.0:.3f}"])
            cmd.extend(["-vn", "-map", "0:a:0"])
            if normalize:
                cmd.extend(["-af", "loudnorm=I=-16:TP=-1.5:LRA=11"])
            cmd.extend(["-c:a", codec_name, "-b:a", f"{max(64, min(320, int(bitrate_kbps)))}k"])
            if out_ext == ".m4a":
                cmd.extend(["-movflags", "+faststart"])
            cmd.append(tmp_path)

            self._run_ffmpeg_with_progress(cmd, dur_s, on_progress, proc_holder, "Audio extraction failed")
            if not os.path.isfile(tmp_path) or os.path.getsize(tmp_path) < 512:
                raise RuntimeError("Extracted audio file was empty")
            dest_path = _resolve_unique_dest(parent, f"{stem}_audio{out_ext}", "rename") or tmp_path
            os.replace(tmp_path, dest_path)
            sz = os.path.getsize(dest_path)
            return {
                "ok": True,
                "path": dest_path,
                "name": os.path.basename(dest_path),
                "oldSizeBytes": old_stat.st_size,
                "newSizeBytes": sz,
                "savedBytes": 0,
                "sizeDeltaBytes": sz,
                "saveMode": "copy",
            }
        except Exception:
            if os.path.exists(tmp_path):
                try:
                    os.remove(tmp_path)
                except OSError:
                    pass
            raise

    def export_gif(
        self,
        path: str,
        start_ms: int = 0,
        end_ms: int = 0,
        fps: int = 15,
        width: int = 640,
        crop: dict[str, Any] | None = None,
        rotate: int = 0,
        flip_h: bool = False,
        flip_v: bool = False,
        speed: float = 1.0,
        on_progress: Any = None,
        proc_holder: dict[str, Any] | None = None,
    ) -> dict[str, Any]:
        """Export a high-quality palette-optimized animated GIF from a video clip."""
        if not self.ffmpeg or not self.ffprobe:
            raise RuntimeError("FFmpeg is required for GIF export")
        if not os.path.isfile(path):
            raise FileNotFoundError(path)
        old_stat = os.stat(path)
        meta = self.probe_video(path, old_stat)
        total_ms = int(meta.get("durationMs", 0) or 0)
        s_ms = max(0, int(start_ms))
        e_ms = int(end_ms) if int(end_ms) > s_ms + 200 else min(total_ms, s_ms + 15000)
        if total_ms > 0:
            e_ms = min(e_ms, total_ms)
        if e_ms <= s_ms + 200:
            raise ValueError("GIF clip must be at least 200ms")
        # Cap accidental huge GIF renders to 45s
        if e_ms - s_ms > 45000:
            e_ms = s_ms + 45000

        spd = max(0.25, min(4.0, float(speed)))
        dur_s = (e_ms - s_ms) / 1000.0
        out_dur_s = dur_s / spd
        fps_v = max(5, min(30, int(fps)))
        w_v = max(160, min(1280, int(width)))
        rot = int(rotate) % 360

        vf_parts: list[str] = []
        if rot == 90:
            vf_parts.append("transpose=1")
        elif rot == 180:
            vf_parts.append("transpose=1,transpose=1")
        elif rot == 270:
            vf_parts.append("transpose=2")
        if flip_h:
            vf_parts.append("hflip")
        if flip_v:
            vf_parts.append("vflip")
        if crop and isinstance(crop, dict):
            x_f = max(0.0, min(0.95, float(crop.get("x", 0.0))))
            y_f = max(0.0, min(0.95, float(crop.get("y", 0.0))))
            w_f = max(0.05, min(1.0 - x_f, float(crop.get("w", 1.0))))
            h_f = max(0.05, min(1.0 - y_f, float(crop.get("h", 1.0))))
            if x_f > 0.005 or y_f > 0.005 or w_f < 0.995 or h_f < 0.995:
                vf_parts.append(
                    f"crop=max(2\\,trunc(iw*{w_f:.5f}/2)*2):max(2\\,trunc(ih*{h_f:.5f}/2)*2):"
                    f"min(iw-2\\,trunc(iw*{x_f:.5f}/2)*2):min(ih-2\\,trunc(ih*{y_f:.5f}/2)*2)"
                )
        if abs(spd - 1.0) > 0.02:
            vf_parts.append(f"setpts={1.0 / spd:.5f}*PTS")
        vf_parts.append(f"fps={fps_v},scale='min({w_v},iw)':-1:flags=lanczos")
        pre_chain = ",".join(vf_parts)
        vf_full = f"{pre_chain},split[s0][s1];[s0]palettegen=stats_mode=diff[p];[s1][p]paletteuse=dither=bayer:bayer_scale=3"

        parent = os.path.dirname(path)
        stem = os.path.splitext(os.path.basename(path))[0]
        tmp_path = os.path.join(parent, f".deckvault_gif_{uuid.uuid4().hex}.gif")
        try:
            cmd = [
                self.ffmpeg, "-y",
                "-ss", f"{s_ms / 1000.0:.3f}",
                "-i", path,
                "-t", f"{dur_s:.3f}",
                "-vf", vf_full,
                "-loop", "0",
                tmp_path,
            ]
            self._run_ffmpeg_with_progress(cmd, out_dur_s, on_progress, proc_holder, "GIF export failed")
            if not os.path.isfile(tmp_path) or os.path.getsize(tmp_path) < 512:
                raise RuntimeError("GIF output was empty")
            dest_path = _resolve_unique_dest(parent, f"{stem}_clip.gif", "rename") or tmp_path
            os.replace(tmp_path, dest_path)
            sz = os.path.getsize(dest_path)
            return {
                "ok": True,
                "path": dest_path,
                "name": os.path.basename(dest_path),
                "oldSizeBytes": old_stat.st_size,
                "newSizeBytes": sz,
                "savedBytes": 0,
                "sizeDeltaBytes": sz,
                "saveMode": "copy",
            }
        except Exception:
            if os.path.exists(tmp_path):
                try:
                    os.remove(tmp_path)
                except OSError:
                    pass
            raise

    def merge_videos(
        self,
        paths: list[str],
        out_dir: str = "",
        output_name: str = "Merged_Video.mp4",
        crf: int = 20,
        on_progress: Any = None,
        proc_holder: dict[str, Any] | None = None,
    ) -> dict[str, Any]:
        """Join 2+ videos into a single MP4, normalizing aspect ratio, FPS, and audio streams."""
        if not self.ffmpeg or not self.ffprobe:
            raise RuntimeError("FFmpeg is required for merging videos")
        clean_paths = [p for p in paths if p and os.path.isfile(p)]
        if len(clean_paths) < 2:
            raise ValueError("Select at least 2 videos to merge")

        metas: list[dict[str, Any]] = []
        total_dur_s = 0.0
        max_w, max_h, max_fps = 0, 0, 30.0
        any_audio = False
        for p in clean_paths:
            m = self.probe_video(p)
            metas.append(m)
            total_dur_s += max(0.2, float(m.get("durationMs", 0) or 0) / 1000.0)
            w = int(m.get("width", 0) or 1280)
            h = int(m.get("height", 0) or 720)
            if w * h > max_w * max_h:
                max_w, max_h = w, h
            if m.get("fps"):
                max_fps = max(max_fps, min(60.0, float(m["fps"])))
            if m.get("audioCodec"):
                any_audio = True

        tw = max(2, min(1920, (max_w // 2) * 2 or 1280))
        th = max(2, min(1920, (max_h // 2) * 2 or 720))
        tfps = max(24, min(60, int(round(max_fps))))

        dest_dir = out_dir if (out_dir and os.path.isdir(out_dir)) else os.path.dirname(clean_paths[0])
        base_name = os.path.basename(str(output_name or "Merged_Video.mp4").strip())
        if not base_name.lower().endswith(".mp4"):
            base_name = os.path.splitext(base_name)[0] + ".mp4"
        tmp_path = os.path.join(dest_dir, f".deckvault_merge_{uuid.uuid4().hex}.mp4")

        try:
            cmd = [self.ffmpeg, "-y"]
            for p in clean_paths:
                cmd.extend(["-i", p])

            fc_parts: list[str] = []
            concat_inputs: list[str] = []
            for idx, m in enumerate(metas):
                fc_parts.append(
                    f"[{idx}:v]scale={tw}:{th}:force_original_aspect_ratio=decrease,"
                    f"pad={tw}:{th}:(ow-iw)/2:(oh-ih)/2:black,setsar=1,fps={tfps},format=yuv420p[v{idx}]"
                )
                concat_inputs.append(f"[v{idx}]")
                if any_audio:
                    if m.get("audioCodec"):
                        fc_parts.append(f"[{idx}:a]aformat=sample_rates=48000:channel_layouts=stereo,aresample=async=1[a{idx}]")
                    else:
                        d_clip = max(0.1, float(m.get("durationMs", 1000) or 1000) / 1000.0)
                        fc_parts.append(f"anullsrc=channel_layout=stereo:sample_rate=48000,atrim=duration={d_clip:.3f}[a{idx}]")
                    concat_inputs.append(f"[a{idx}]")

            fc_parts.append(f"{''.join(concat_inputs)}concat=n={len(clean_paths)}:v=1:a={1 if any_audio else 0}[vout]{'[aout]' if any_audio else ''}")
            cmd.extend(["-filter_complex", ";".join(fc_parts), "-map", "[vout]"])
            if any_audio:
                cmd.extend(["-map", "[aout]", "-c:a", "aac", "-b:a", "192k"])
            else:
                cmd.append("-an")
            cmd.extend([
                "-c:v", "libx264", "-preset", "veryfast", "-crf", str(max(16, min(28, int(crf)))),
                "-pix_fmt", "yuv420p", "-movflags", "+faststart", tmp_path,
            ])

            self._run_ffmpeg_with_progress(cmd, total_dur_s, on_progress, proc_holder, "Video merge failed")
            if not os.path.isfile(tmp_path) or os.path.getsize(tmp_path) < 1024:
                raise RuntimeError("Merged video output was empty")
            dest_path = _resolve_unique_dest(dest_dir, base_name, "rename") or tmp_path
            os.replace(tmp_path, dest_path)
            st_new = os.stat(dest_path)
            new_meta = self.probe_video(dest_path, st_new)
            self.cache.put(dest_path, st_new.st_mtime_ns, st_new.st_size, new_meta)
            return {
                "ok": True,
                "path": dest_path,
                "name": os.path.basename(dest_path),
                "oldSizeBytes": sum(os.path.getsize(p) for p in clean_paths if os.path.isfile(p)),
                "newSizeBytes": st_new.st_size,
                "savedBytes": 0,
                "sizeDeltaBytes": st_new.st_size,
                "newDurationMs": new_meta.get("durationMs", 0),
                "saveMode": "copy",
            }
        except Exception:
            if os.path.exists(tmp_path):
                try:
                    os.remove(tmp_path)
                except OSError:
                    pass
            raise


class FleetStore:
    """Persists remote paired devices (Laptop, Tablet, Fold, iPhone) for the PC Desktop App."""

    def __init__(self) -> None:
        self._lock = threading.Lock()
        self.path = CONFIG_DIR / "fleet_hosts.json"
        self.hosts: list[dict[str, Any]] = []
        self._load()

    def _load(self) -> None:
        if self.path.exists():
            try:
                with open(self.path, "r", encoding="utf-8") as f:
                    data = json.load(f)
                if isinstance(data, list):
                    self.hosts = data
            except Exception:
                self.hosts = []

    def _save(self) -> None:
        tmp = self.path.with_suffix(".tmp")
        with open(tmp, "w", encoding="utf-8") as f:
            json.dump(self.hosts, f, indent=2)
        os.replace(tmp, self.path)

    def public_list(self) -> list[dict[str, Any]]:
        with self._lock:
            return [
                {
                    "hostId": h.get("hostId", ""),
                    "name": h.get("name", ""),
                    "label": h.get("label", h.get("name", "")),
                    "ip": h.get("ip", ""),
                    "port": int(h.get("port", DEFAULT_HTTP_PORT)),
                    "os": h.get("os", "Device"),
                }
                for h in self.hosts
            ]

    def get(self, host_id: str) -> dict[str, Any] | None:
        with self._lock:
            for h in self.hosts:
                if h.get("hostId") == host_id:
                    return dict(h)
        return None

    def upsert(self, host: dict[str, Any]) -> None:
        with self._lock:
            self.hosts = [h for h in self.hosts if h.get("hostId") != host.get("hostId")]
            self.hosts.insert(0, host)
            self._save()

    def remove(self, host_id: str) -> None:
        with self._lock:
            self.hosts = [h for h in self.hosts if h.get("hostId") != host_id]
            self._save()


class FavoritesStore:
    """Persists favorite folders across all devices (PC, Laptop, Tablet, Fold, iPhone)."""

    def __init__(self) -> None:
        self._lock = threading.Lock()
        self.path = CONFIG_DIR / "favorites.json"
        self.items: list[dict[str, Any]] = []
        self._load()

    def _load(self) -> None:
        if self.path.exists():
            try:
                with open(self.path, "r", encoding="utf-8") as f:
                    data = json.load(f)
                if isinstance(data, list):
                    self.items = data
            except Exception:
                self.items = []

    def _save(self) -> None:
        tmp = self.path.with_suffix(".tmp")
        with open(tmp, "w", encoding="utf-8") as f:
            json.dump(self.items, f, indent=2)
        os.replace(tmp, self.path)

    def list_all(self) -> list[dict[str, Any]]:
        with self._lock:
            return list(self.items)

    def toggle(self, item: dict[str, Any]) -> tuple[bool, list[dict[str, Any]]]:
        hid = str(item.get("hostId", ""))
        path = str(item.get("path", ""))
        if not hid or not path:
            raise ValueError("hostId and path are required")
        with self._lock:
            exists = any(x.get("hostId") == hid and x.get("path") == path for x in self.items)
            if exists:
                self.items = [x for x in self.items if not (x.get("hostId") == hid and x.get("path") == path)]
                added = False
            else:
                clean_name = str(item.get("name") or os.path.basename(path.rstrip("\\/")) or path)
                self.items.insert(0, {
                    "hostId": hid,
                    "hostName": str(item.get("hostName", "Device")),
                    "os": str(item.get("os", "Device")),
                    "path": path,
                    "name": clean_name,
                })
                added = True
            self._save()
            return added, list(self.items)


def detect_gdrive_environment(config_data: dict[str, Any]) -> dict[str, Any]:
    """Detect Google Drive for Desktop sync paths and configured rclone remotes."""
    detected_dirs: list[str] = []
    if os.name == "nt":
        for letter in "DEFGHIJKLMNOPQRSTUVWXYZ":
            cand = f"{letter}:\\My Drive"
            if os.path.isdir(cand):
                detected_dirs.append(cand)
        for base in [
            Path.home() / "Google Drive",
            Path.home() / "My Drive",
        ]:
            if base.is_dir() and str(base) not in detected_dirs:
                detected_dirs.append(str(base))

    rclone_exe = shutil.which("rclone")
    rclone_remotes: list[str] = []
    if rclone_exe:
        try:
            proc = subprocess.run([rclone_exe, "listremotes"], capture_output=True, text=True, timeout=5, creationflags=SUBPROCESS_FLAGS)
            if proc.returncode == 0:
                rclone_remotes = [ln.strip() for ln in proc.stdout.splitlines() if ln.strip()]
        except Exception:
            pass

    saved_dir = str(config_data.get("gdrive_backup_dir", "") or "")
    saved_remote = str(config_data.get("gdrive_rclone_remote", "") or "")
    if not saved_dir and detected_dirs:
        saved_dir = os.path.join(detected_dirs[0], "DeckVault Backup")

    drive_app_installed = os.path.isdir(r"C:\Program Files\Google\Drive File Stream") or bool(detected_dirs)
    return {
        "driveAppInstalled": drive_app_installed,
        "detectedDirs": detected_dirs,
        "savedDir": saved_dir,
        "rcloneAvailable": bool(rclone_exe),
        "rcloneRemotes": rclone_remotes,
        "savedRemote": saved_remote,
    }


def _stat_cloud_usage(path: str) -> tuple[int, int]:
    try:
        u = shutil.disk_usage(path)
        return int(u.free), int(u.total)
    except OSError:
        return 0, 0


def detect_cloud_storage_providers(config_data: dict[str, Any]) -> list[dict[str, Any]]:
    """Detect Google Drive, OneDrive, Dropbox, iCloud, MEGA & custom cloud/network folders."""
    providers: list[dict[str, Any]] = []
    seen_paths: set[str] = set()

    def add_entry(pid: str, name: str, provider_type: str, raw_path: str, custom: bool = False) -> None:
        if not raw_path:
            return
        norm = os.path.normpath(raw_path)
        if len(norm) == 2 and norm[1] == ":":
            norm += "\\"
        if not os.path.isdir(norm):
            return
        key = norm.lower()
        if key in seen_paths:
            return
        seen_paths.add(key)
        free_b, total_b = _stat_cloud_usage(norm)
        providers.append({
            "id": pid,
            "name": name,
            "provider": provider_type,
            "path": norm,
            "freeBytes": free_b,
            "totalBytes": total_b,
            "custom": custom,
            "online": True,
        })

    # 1. Google Drive (virtual mount X:\My Drive or Drive File Stream / Desktop)
    gd_env = detect_gdrive_environment(config_data)
    for idx, d in enumerate(gd_env.get("detectedDirs") or []):
        add_entry(f"gdrive_{idx}", "Google Drive", "gdrive", d)
    if os.name == "nt":
        for letter in "DEFGHIJKLMNOPQRSTUVWXYZ":
            root = f"{letter}:\\"
            if os.path.isdir(root) and "google" in get_drive_volume_label(root).lower():
                my_d = os.path.join(root, "My Drive")
                add_entry(f"gdrive_vol_{letter}", "Google Drive", "gdrive", my_d if os.path.isdir(my_d) else root)

    # 2. OneDrive / OneDrive for Business
    for env_k in ("OneDrive", "OneDriveConsumer", "OneDriveCommercial"):
        val = os.environ.get(env_k, "")
        if val:
            lbl = "OneDrive" if env_k != "OneDriveCommercial" else "OneDrive Work"
            add_entry(f"onedrive_{env_k}", lbl, "onedrive", val)
    home = Path.home()
    add_entry("onedrive_home", "OneDrive", "onedrive", str(home / "OneDrive"))

    # 3. Dropbox (via info.json or ~\Dropbox)
    for info_base in (os.environ.get("LOCALAPPDATA", ""), os.environ.get("APPDATA", "")):
        if info_base:
            info_file = os.path.join(info_base, "Dropbox", "info.json")
            if os.path.isfile(info_file):
                try:
                    with open(info_file, "r", encoding="utf-8") as df:
                        dinfo = json.load(df)
                    for acct_k in ("personal", "business"):
                        p = (dinfo.get(acct_k) or {}).get("path", "")
                        if p:
                            add_entry(f"dropbox_{acct_k}", "Dropbox" if acct_k == "personal" else "Dropbox Business", "dropbox", p)
                except Exception:
                    pass
    add_entry("dropbox_home", "Dropbox", "dropbox", str(home / "Dropbox"))

    # 4. iCloud Drive / MEGA / Box / Nextcloud
    add_entry("icloud_home", "iCloud Drive", "icloud", str(home / "iCloudDrive"))
    add_entry("mega_home", "MEGA", "mega", str(home / "MEGA"))
    add_entry("box_home", "Box", "box", str(home / "Box"))
    add_entry("nextcloud_home", "Nextcloud", "nextcloud", str(home / "Nextcloud"))

    # 5. User-connected storage apps or custom network/cloud mounts
    for item in (config_data.get("cloud_providers", []) or []):
        if isinstance(item, dict) and item.get("path"):
            add_entry(
                str(item.get("id") or f"custom_{uuid.uuid4().hex[:8]}"),
                str(item.get("name") or "Cloud Folder"),
                str(item.get("provider") or "custom"),
                str(item.get("path")),
                custom=True,
            )

    return providers


DRAG_STAGING_DIR = CONFIG_DIR / "drag_staging"


def _prepare_local_drag_files(rt: CompanionRuntime, host_id: str, paths: list[str]) -> list[str]:
    """Resolve local file paths or stage remote files locally so Explorer/Dropbox can accept drops."""
    local_id = rt.config.data["host_id"]
    out_paths: list[str] = []
    if not host_id or host_id == local_id:
        for raw_p in paths:
            safe = rt.config.resolve_safe_path(str(raw_p))
            if os.path.exists(safe):
                out_paths.append(safe)
        return out_paths

    host = rt.fleet.get(host_id)
    if not host:
        raise ValueError("Source paired device is not available")
    DRAG_STAGING_DIR.mkdir(parents=True, exist_ok=True)
    for raw_p in paths:
        src_p = str(raw_p)
        fname = os.path.basename(src_p.rstrip("\\/")) or f"file_{int(time.time())}"
        target = _resolve_unique_dest(str(DRAG_STAGING_DIR), fname, "overwrite")
        assert target is not None
        q = url_quote(src_p, safe="")
        req = urllib.request.Request(
            f"http://{host['ip']}:{host['port']}/api/stream?path={q}",
            headers={"X-DeckVault-Token": host["token"]},
            method="GET",
        )
        with urllib.request.urlopen(req, timeout=180) as resp, open(target, "wb") as out:
            shutil.copyfileobj(resp, out)
        out_paths.append(target)
    return out_paths


def _set_windows_file_clipboard(local_paths: list[str]) -> None:
    """Copy real Windows FileDrop paths (CF_HDROP) onto the system clipboard."""
    if os.name != "nt" or not local_paths:
        return
    done = threading.Event()
    err_holder: list[Exception] = []

    def sta_worker() -> None:
        try:
            import clr
            clr.AddReference("System.Windows.Forms")
            import System
            from System.Windows.Forms import Clipboard
            from System.Collections.Specialized import StringCollection
            from System.Threading import ApartmentState, Thread

            def run_clip() -> None:
                try:
                    sc = StringCollection()
                    for p in local_paths:
                        sc.Add(os.path.abspath(p))
                    Clipboard.SetFileDropList(sc)
                except Exception as exc:
                    err_holder.append(exc)
                finally:
                    done.set()

            t = Thread(System.Threading.ThreadStart(run_clip))
            t.SetApartmentState(ApartmentState.STA)
            t.Start()
            t.Join(4000)
        except Exception as exc:
            err_holder.append(exc)
            done.set()

    sta_worker()
    done.wait(4.5)
    if err_holder:
        raise err_holder[0]


def discover_all_lan_hosts(self_host_id: str, http_port: int = DEFAULT_HTTP_PORT, udp_port: int = DEFAULT_UDP_PORT) -> list[dict[str, Any]]:
    """Find DeckVault devices over Wi-Fi via both UDP broadcast and fast TCP /24 ping sweep."""
    found: dict[str, dict[str, Any]] = {}
    lock = threading.Lock()

    def merge_host(item: dict[str, Any]) -> None:
        hid = str(item.get("hostId", ""))
        if not hid or hid == self_host_id or item.get("service") != "DeckVault":
            return
        with lock:
            prev = found.get(hid)
            if prev:
                ips = list(dict.fromkeys([*(item.get("ips") or []), *(prev.get("ips") or [])]))
                prev["ips"] = [i for i in ips if i and not i.startswith("127.")]
                if item.get("freeBytes", 0) > prev.get("freeBytes", 0):
                    prev["freeBytes"] = item["freeBytes"]
                    prev["totalBytes"] = item.get("totalBytes", 0)
            else:
                found[hid] = item

    def run_udp() -> None:
        sock = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
        try:
            sock.setsockopt(socket.SOL_SOCKET, socket.SO_BROADCAST, 1)
            sock.settimeout(0.45)
            targets = {"255.255.255.255"}
            for ip in get_local_ipv4_addresses():
                parts = ip.split(".")
                if len(parts) == 4:
                    targets.add(f"{parts[0]}.{parts[1]}.{parts[2]}.255")
            for _ in range(2):
                for tgt in targets:
                    try:
                        sock.sendto(DISCOVERY_MAGIC, (tgt, udp_port))
                    except OSError:
                        pass
                time.sleep(0.12)
            deadline = time.time() + 1.8
            while time.time() < deadline:
                try:
                    data, addr = sock.recvfrom(4096)
                    obj = json.loads(data.decode("utf-8"))
                    ips = [addr[0], *(obj.get("ips") or [])]
                    obj["ips"] = [i for i in dict.fromkeys(ips) if i and not i.startswith("127.")]
                    merge_host(obj)
                except socket.timeout:
                    pass
                except Exception:
                    pass
        finally:
            sock.close()

    def probe_ip(ip: str) -> None:
        try:
            with socket.create_connection((ip, http_port), timeout=0.32):
                pass
            req = urllib.request.Request(f"http://{ip}:{http_port}/api/ping", method="GET")
            with urllib.request.urlopen(req, timeout=0.65) as resp:
                if resp.status == 200:
                    ping = json.loads(resp.read().decode("utf-8"))
                    merge_host({
                        "service": ping.get("service", ""),
                        "version": ping.get("version", ""),
                        "hostId": ping.get("hostId", ""),
                        "deviceName": ping.get("deviceName", ip),
                        "port": http_port,
                        "ips": [ip],
                        "freeBytes": 0,
                        "totalBytes": 0,
                        "ffmpeg": bool(ping.get("ffmpeg", False)),
                        "os": ping.get("os", "Device"),
                    })
        except Exception:
            pass

    own_ips = set(get_local_ipv4_addresses())
    subnet_candidates: list[str] = []
    seen_prefixes: set[str] = set()
    for ip in own_ips:
        if ip.startswith(("192.168.", "10.", "172.")):
            parts = ip.split(".")
            if len(parts) == 4:
                pref = ".".join(parts[:3])
                if pref not in seen_prefixes:
                    seen_prefixes.add(pref)
                    for host_num in range(1, 255):
                        cand = f"{pref}.{host_num}"
                        if cand not in own_ips:
                            subnet_candidates.append(cand)

    with concurrent.futures.ThreadPoolExecutor(max_workers=64) as pool:
        f_udp = pool.submit(run_udp)
        futs = [pool.submit(probe_ip, cand) for cand in subnet_candidates]
        f_udp.result()
        concurrent.futures.wait(futs, timeout=2.5)

    return sorted(found.values(), key=lambda d: (0 if d.get("os") == "Windows" else 1, d.get("deviceName", "").lower()))


class JobManager:
    """Sequential background job queue with live FFmpeg progress and cancellation."""

    ROUTE_BY_TYPE = {
        "edit": "/api/video/edit",
        "compress": "/api/media/compress",
        "upscale": "/api/media/upscale",
        "extract_audio": "/api/media/extract-audio",
        "gif": "/api/media/gif",
        "merge": "/api/media/merge",
    }

    def __init__(self, rt: "CompanionRuntime") -> None:
        self.rt = rt
        self._lock = threading.Lock()
        self._jobs: dict[str, dict[str, Any]] = {}
        self._order: list[str] = []
        self._proc_holders: dict[str, dict[str, Any]] = {}
        self._q: queue.Queue[tuple[str, str, dict[str, Any]]] = queue.Queue()
        self._worker = threading.Thread(target=self._run_worker, name="DeckVaultJobWorker", daemon=True)
        self._worker.start()

    @staticmethod
    def _build_title(job_type: str, payload: dict[str, Any]) -> str:
        name = os.path.basename(str(payload.get("path", "")).rstrip("\\/"))
        if job_type == "upscale":
            return f"Upscale ({str(payload.get('preset', '2x')).upper()}): {name}"
        if job_type == "compress":
            return f"Compress: {name}"
        if job_type == "edit":
            return f"Edit Studio: {name}"
        if job_type == "extract_audio":
            return f"Extract Audio ({str(payload.get('format', 'm4a')).upper()}): {name}"
        if job_type == "gif":
            return f"GIF Clip: {name}"
        if job_type == "merge":
            cnt = len(payload.get("paths") or [])
            return f"Merge {cnt} Videos"
        return f"{job_type}: {name}"

    def enqueue(self, job_type: str, payload: dict[str, Any]) -> dict[str, Any]:
        route = self.ROUTE_BY_TYPE.get(job_type)
        if not route:
            raise ValueError(f"Unsupported job type: {job_type}")
        job_id = f"job_{uuid.uuid4().hex[:10]}"
        src_path = str(payload.get("path") or ((payload.get("paths") or [""])[0]))
        host_id = str(payload.get("hostId") or self.rt.config.data["host_id"])
        rec = {
            "id": job_id,
            "type": job_type,
            "route": route,
            "title": str(payload.get("title") or self._build_title(job_type, payload)),
            "hostId": host_id,
            "sourcePath": src_path,
            "sourceName": os.path.basename(src_path.rstrip("\\/")) or "Media",
            "status": "queued",
            "progressPct": 0.0,
            "speed": "",
            "remainingSec": None,
            "message": "Queued",
            "createdAt": time.time(),
            "startedAt": None,
            "finishedAt": None,
            "outputPath": "",
            "outputDir": "",
            "outputName": "",
            "outputMediaType": "",
            "newSizeBytes": 0,
            "savedBytes": 0,
            "error": "",
        }
        with self._lock:
            self._jobs[job_id] = rec
            self._order.insert(0, job_id)
            self._proc_holders[job_id] = {"proc": None, "cancel": False}
            if len(self._order) > 60:
                for old_id in list(self._order[60:]):
                    old_j = self._jobs.get(old_id)
                    if old_j and old_j["status"] in ("done", "error", "cancelled"):
                        self._jobs.pop(old_id, None)
                        self._proc_holders.pop(old_id, None)
                        self._order.remove(old_id)
        self._q.put((job_id, route, dict(payload)))
        return dict(rec)

    def snapshot(self) -> dict[str, Any]:
        with self._lock:
            jobs = [dict(self._jobs[jid]) for jid in self._order if jid in self._jobs]
        active = [j for j in jobs if j["status"] == "running"]
        queued = [j for j in jobs if j["status"] == "queued"]
        overall = round(active[0]["progressPct"], 1) if active else (0.0 if queued else 100.0)
        return {
            "jobs": jobs,
            "runningCount": len(active),
            "queuedCount": len(queued),
            "activeCount": len(active) + len(queued),
            "overallPct": overall,
        }

    def cancel(self, job_id: str) -> dict[str, Any]:
        with self._lock:
            rec = self._jobs.get(job_id)
            holder = self._proc_holders.get(job_id)
            if not rec or not holder:
                raise KeyError("Job not found")
            if rec["status"] in ("done", "error", "cancelled"):
                return dict(rec)
            holder["cancel"] = True
            proc = holder.get("proc")
            if rec["status"] == "queued":
                rec["status"] = "cancelled"
                rec["finishedAt"] = time.time()
                rec["message"] = "Cancelled"
            else:
                rec["message"] = "Cancelling..."
        if proc is not None:
            try:
                proc.terminate()
            except Exception:
                pass
        return self.get(job_id) or {}

    def get(self, job_id: str) -> dict[str, Any] | None:
        with self._lock:
            j = self._jobs.get(job_id)
            return dict(j) if j else None

    def clear_finished(self) -> int:
        removed = 0
        with self._lock:
            keep: list[str] = []
            for jid in self._order:
                j = self._jobs.get(jid)
                if j and j["status"] in ("done", "error", "cancelled"):
                    self._jobs.pop(jid, None)
                    self._proc_holders.pop(jid, None)
                    removed += 1
                else:
                    keep.append(jid)
            self._order = keep
        return removed

    def _run_worker(self) -> None:
        while True:
            job_id, route, payload = self._q.get()
            try:
                with self._lock:
                    rec = self._jobs.get(job_id)
                    holder = self._proc_holders.get(job_id)
                    if not rec or not holder or holder.get("cancel") or rec["status"] == "cancelled":
                        continue
                    rec["status"] = "running"
                    rec["startedAt"] = time.time()
                    rec["progressPct"] = 2.0
                    rec["message"] = "Processing..."

                def _on_prog(info: dict[str, Any]) -> None:
                    with self._lock:
                        r = self._jobs.get(job_id)
                        if not r or r["status"] != "running":
                            return
                        r["progressPct"] = float(info.get("progressPct", r["progressPct"]))
                        r["speed"] = str(info.get("speed", "") or "")
                        r["remainingSec"] = info.get("remainingSec")
                        spd_bit = f" • {r['speed']}" if r["speed"] else ""
                        eta_bit = f" • {int(round(r['remainingSec']))}s left" if r["remainingSec"] is not None else ""
                        r["message"] = f"{r['progressPct']:.0f}%{spd_bit}{eta_bit}"

                res = self.rt.run_or_relay_media_op(route, payload, on_progress=_on_prog, proc_holder=holder)
                out_p = str(res.get("path", "") or "")
                with self._lock:
                    r = self._jobs.get(job_id)
                    if r:
                        r["status"] = "done"
                        r["progressPct"] = 100.0
                        r["remainingSec"] = 0
                        r["finishedAt"] = time.time()
                        r["outputPath"] = out_p
                        r["outputDir"] = os.path.dirname(out_p) if out_p else ""
                        r["outputName"] = str(res.get("name") or (os.path.basename(out_p) if out_p else ""))
                        r["outputMediaType"] = classify_media_type(out_p) if out_p else ""
                        r["newSizeBytes"] = int(res.get("newSizeBytes", 0) or 0)
                        r["savedBytes"] = int(res.get("savedBytes", 0) or 0)
                        r["message"] = "Completed"
            except InterruptedError:
                with self._lock:
                    r = self._jobs.get(job_id)
                    if r:
                        r["status"] = "cancelled"
                        r["finishedAt"] = time.time()
                        r["message"] = "Cancelled"
            except Exception as exc:
                self.rt.log(f"Queued job {job_id} error: {exc}")
                with self._lock:
                    r = self._jobs.get(job_id)
                    if r:
                        r["status"] = "error"
                        r["finishedAt"] = time.time()
                        r["error"] = str(exc)
                        r["message"] = "Failed"
            finally:
                self._q.task_done()


class CompanionRuntime:
    """Shared application state accessed by HTTP handlers and optional GUI."""

    def __init__(self) -> None:
        self.config = ServerConfig()
        self.fleet = FleetStore()
        self.favorites = FavoritesStore()
        self.desktop_token = secrets.token_urlsafe(32)
        self.cache = MetadataCache(CACHE_DB_PATH)
        self.indexer = DiskIndexer(self.config.get_roots)
        self.media = MediaService(self.cache)
        self.jobs = JobManager(self)
        self.active_clients: dict[str, float] = {}
        self._clients_lock = threading.Lock()
        self.events: queue.Queue[str] = queue.Queue(maxsize=200)

    def exec_local_media_op(
        self,
        route: str,
        path_str: str,
        body: dict[str, Any],
        force_copy: bool = False,
        on_progress: Any = None,
        proc_holder: dict[str, Any] | None = None,
    ) -> dict[str, Any]:
        if route == "/api/media/compress-estimate":
            return self.media.estimate_compression(
                path=path_str,
                target_height=int(body.get("targetHeight", 0) or 0),
                crf=int(body.get("crf", 24) or 24),
                codec=str(body.get("codec", "h264")),
                audio_kbps=int(body.get("audioKbps", 128) if body.get("audioKbps") is not None else 128),
                target_mb=float(body.get("targetMb", 0.0) or 0.0),
                sample_probe=bool(body.get("sampleProbe", False)),
                quality=int(body.get("quality", 82) or 82),
            )
        save_mode = "copy" if force_copy else str(body.get("saveMode", "copy"))
        recycle = bool(body.get("recycleOriginal", True))

        if route == "/api/video/edit":
            res = self.media.edit_video(
                path=path_str,
                start_ms=int(body.get("startMs", 0) or 0),
                end_ms=int(body.get("endMs", 0) or 0),
                crop=body.get("crop"),
                rotate=int(body.get("rotate", 0) or 0),
                flip_h=bool(body.get("flipH", False)),
                flip_v=bool(body.get("flipV", False)),
                speed=float(body.get("speed", 1.0) or 1.0),
                mute_audio=bool(body.get("muteAudio", False)),
                mode=str(body.get("mode", "lossless")),
                crf=int(body.get("crf", 22) or 22),
                save_mode=str(body.get("saveMode", "overwrite")) if not force_copy else "copy",
                recycle_original=recycle,
                brightness=float(body.get("brightness", 0.0) or 0.0),
                contrast=float(body.get("contrast", 1.0) if body.get("contrast") is not None else 1.0),
                saturation=float(body.get("saturation", 1.0) if body.get("saturation") is not None else 1.0),
                sharpen=float(body.get("sharpen", 0.0) or 0.0),
                deband=bool(body.get("deband", False)),
                stabilize=bool(body.get("stabilize", False)),
                fade_sec=float(body.get("fadeSec", 0.0) or 0.0),
                normalize_audio=bool(body.get("normalizeAudio", False)),
                on_progress=on_progress,
                proc_holder=proc_holder,
            )
            if not force_copy:
                self.indexer.adjust_ancestors(res["path"], res["sizeDeltaBytes"], 1 if res.get("saveMode") == "copy" else 0, 0)
                res["totalSpaceSavedBytes"] = self.config.add_space_saved(res["savedBytes"])
            return res

        if route == "/api/media/compress":
            res = self.media.compress_media(
                path=path_str,
                target_height=int(body.get("targetHeight", 0) or 0),
                crf=int(body.get("crf", 24) or 24),
                codec=str(body.get("codec", "h264")),
                audio_kbps=int(body.get("audioKbps", 128) if body.get("audioKbps") is not None else 128),
                target_mb=float(body.get("targetMb", 0.0) or 0.0),
                save_mode=save_mode,
                recycle_original=recycle,
                quality=int(body.get("quality", 82) or 82),
                on_progress=on_progress,
                proc_holder=proc_holder,
            )
            if not force_copy:
                self.indexer.adjust_ancestors(res["path"], res["sizeDeltaBytes"], 1 if res["saveMode"] == "copy" else 0, 0)
                res["totalSpaceSavedBytes"] = self.config.add_space_saved(res["savedBytes"])
            return res

        if route == "/api/media/extract-audio":
            res = self.media.extract_audio(
                path=path_str,
                fmt=str(body.get("format", "m4a")),
                bitrate_kbps=int(body.get("bitrateKbps", 192) or 192),
                normalize=bool(body.get("normalize", False)),
                start_ms=int(body.get("startMs", 0) or 0),
                end_ms=int(body.get("endMs", 0) or 0),
                on_progress=on_progress,
                proc_holder=proc_holder,
            )
            if not force_copy:
                self.indexer.adjust_ancestors(res["path"], res["newSizeBytes"], 1, 0)
            return res

        if route == "/api/media/gif":
            res = self.media.export_gif(
                path=path_str,
                start_ms=int(body.get("startMs", 0) or 0),
                end_ms=int(body.get("endMs", 0) or 0),
                fps=int(body.get("fps", 15) or 15),
                width=int(body.get("width", 640) or 640),
                crop=body.get("crop"),
                rotate=int(body.get("rotate", 0) or 0),
                flip_h=bool(body.get("flipH", False)),
                flip_v=bool(body.get("flipV", False)),
                speed=float(body.get("speed", 1.0) or 1.0),
                on_progress=on_progress,
                proc_holder=proc_holder,
            )
            if not force_copy:
                self.indexer.adjust_ancestors(res["path"], res["newSizeBytes"], 1, 1)
            return res

        res = self.media.upscale_media(
            path=path_str,
            preset=str(body.get("preset", "2x")),
            sharpen=float(body.get("sharpen", 0.9) if body.get("sharpen") is not None else 0.9),
            denoise=bool(body.get("denoise", False)),
            fps60=bool(body.get("fps60", False)),
            crf=int(body.get("crf", 19) or 19),
            save_mode=save_mode,
            recycle_original=recycle,
            mode=str(body.get("mode", "cas")),
            denoise_mode=str(body.get("denoiseMode", "off")),
            deband=bool(body.get("deband", False)),
            stabilize=bool(body.get("stabilize", False)),
            on_progress=on_progress,
            proc_holder=proc_holder,
        )
        if not force_copy:
            self.indexer.adjust_ancestors(res["path"], res["sizeDeltaBytes"], 1 if res["saveMode"] == "copy" else 0, 0)
        return res

    def run_or_relay_media_op(
        self,
        route: str,
        body: dict[str, Any],
        on_progress: Any = None,
        proc_holder: dict[str, Any] | None = None,
    ) -> dict[str, Any]:
        local_id = self.config.data["host_id"]
        host_id = str(body.get("hostId") or local_id)

        if route == "/api/media/merge":
            raw_paths = [str(p) for p in (body.get("paths") or []) if str(p).strip()]
            out_dir_raw = str(body.get("outDir", "") or "")
            out_name = str(body.get("outputName", "Merged_Video.mp4") or "Merged_Video.mp4")
            crf = int(body.get("crf", 20) or 20)
            if host_id == local_id:
                safe_paths = [self.config.resolve_safe_path(p) for p in raw_paths]
                safe_dir = self.config.resolve_safe_path(out_dir_raw) if out_dir_raw else ""
                res = self.media.merge_videos(safe_paths, safe_dir, out_name, crf, on_progress, proc_holder)
                self.indexer.adjust_ancestors(res["path"], res["newSizeBytes"], 1, 1)
                self.log(f"Merged {len(safe_paths)} videos -> {os.path.basename(res['path'])}")
                return res

        raw_path = str(body.get("path", ""))
        if host_id == local_id:
            safe = self.config.resolve_safe_path(raw_path)
            res = self.exec_local_media_op(route, safe, body, force_copy=False, on_progress=on_progress, proc_holder=proc_holder)
            if route != "/api/media/compress-estimate":
                self.log(f"{route.rsplit('/', 1)[-1].title()} {os.path.basename(res['path'])}")
            return res

        host = self.fleet.get(host_id)
        if not host:
            raise ValueError("Paired device not found")

        try:
            req = urllib.request.Request(
                f"http://{host['ip']}:{host['port']}{route}",
                data=json.dumps(body).encode("utf-8"),
                headers={"X-DeckVault-Token": host["token"], "Content-Type": "application/json"},
                method="POST",
            )
            with urllib.request.urlopen(req, timeout=3600) as resp:
                return json.loads(resp.read().decode("utf-8"))
        except urllib.error.HTTPError as exc:
            err_txt = exc.read().decode("utf-8", errors="replace")
            if exc.code != 404 and "Unknown route" not in err_txt:
                raise RuntimeError(err_txt or f"HTTP {exc.code}")

        if route == "/api/media/compress-estimate" and not bool(body.get("sampleProbe", False)):
            old_sz = int(body.get("sizeBytes") or 0)
            ow = int(body.get("width") or 1920)
            oh = int(body.get("height") or 1080)
            dur_ms = int(body.get("durationMs") or 10000)
            dur_s = max(0.5, dur_ms / 1000.0)
            th = int(body.get("targetHeight", 0) or 0)
            crf = int(body.get("crf", 24) or 24)
            codec = str(body.get("codec", "h264"))
            audio_kbps = int(body.get("audioKbps", 128) if body.get("audioKbps") is not None else 128)
            target_mb = float(body.get("targetMb", 0.0) or 0.0)
            use_hevc = (codec.lower() in ("h265", "hevc")) and self.media.has_encoder("libx265")
            scale = (th / oh) if (th > 0 and th < oh) else 1.0
            nw = max(2, int(round(ow * scale)) // 2 * 2)
            nh = max(2, int(round(oh * scale)) // 2 * 2)
            if target_mb > 0:
                est_bytes = max(32 * 1024, int(target_mb * 1024 * 1024))
                method = "target_size"
            else:
                pix_ratio = (nw * nh) / max(1, ow * oh)
                crf_factor = 2.0 ** ((23.0 - float(crf)) / 6.0)
                codec_factor = 0.66 if use_hevc else 1.0
                src_bps = int((old_sz * 8) / dur_s) if old_sz > 0 else 4_000_000
                base_v_bps = min(max(src_bps * 0.85, 400_000), max(3_500_000 * pix_ratio, src_bps * 0.92))
                est_v_bps = max(160_000, base_v_bps * (pix_ratio ** 0.82) * crf_factor * codec_factor)
                est_bytes = max(32 * 1024, int(((est_v_bps + max(0, audio_kbps) * 1000) / 8.0) * dur_s))
                method = "analytical"
            return {
                "mediaType": classify_media_type(raw_path),
                "oldSizeBytes": old_sz,
                "estimatedBytes": est_bytes,
                "oldWidth": ow,
                "oldHeight": oh,
                "newWidth": nw,
                "newHeight": nh,
                "durationMs": dur_ms,
                "method": method,
                "encoderUsed": "libx265" if use_hevc else "libx264",
            }

        stage_dir = DRAG_STAGING_DIR / uuid.uuid4().hex
        stage_dir.mkdir(parents=True, exist_ok=True)
        try:
            def _dl_remote(rpath: str) -> str:
                base = rpath.rsplit("\\", 1)[-1].rsplit("/", 1)[-1] or f"media_{uuid.uuid4().hex[:6]}.mp4"
                dest = str(stage_dir / base)
                q = url_quote(rpath, safe="")
                sreq = urllib.request.Request(
                    f"http://{host['ip']}:{host['port']}/api/stream?path={q}",
                    headers={"X-DeckVault-Token": host["token"]},
                    method="GET",
                )
                with urllib.request.urlopen(sreq, timeout=600) as resp, open(dest, "wb") as out:
                    shutil.copyfileobj(resp, out)
                return dest

            if route == "/api/media/merge":
                raw_paths = [str(p) for p in (body.get("paths") or []) if str(p).strip()]
                local_paths = [_dl_remote(rp) for rp in raw_paths]
                first_rp = raw_paths[0]
                sep = "\\" if "\\" in first_rp else "/"
                remote_dir = first_rp.rsplit(sep, 1)[0] if sep in first_rp else ""
                res = self.media.merge_videos(
                    local_paths, str(stage_dir), str(body.get("outputName", "Merged_Video.mp4")),
                    int(body.get("crf", 20) or 20), on_progress, proc_holder,
                )
            else:
                sep = "\\" if "\\" in raw_path else "/"
                remote_dir = raw_path.rsplit(sep, 1)[0] if sep in raw_path else ""
                local_src = _dl_remote(raw_path)
                res = self.exec_local_media_op(
                    route, local_src, body,
                    force_copy=(route != "/api/media/compress-estimate"),
                    on_progress=on_progress, proc_holder=proc_holder,
                )
                if route == "/api/media/compress-estimate":
                    return res

            out_local = res["path"]
            out_name = os.path.basename(out_local)
            up_url = (
                f"http://{host['ip']}:{host['port']}/api/file/upload"
                f"?dir={url_quote(remote_dir, safe='')}&name={url_quote(out_name, safe='')}&conflict=rename"
            )
            with open(out_local, "rb") as uf:
                up_data = uf.read()
            ureq = urllib.request.Request(
                up_url, data=up_data,
                headers={"X-DeckVault-Token": host["token"], "Content-Type": "application/octet-stream"},
                method="POST",
            )
            with urllib.request.urlopen(ureq, timeout=600) as uresp:
                if uresp.status >= 400:
                    raise OSError(f"Remote upload failed: HTTP {uresp.status}")

            want_overwrite = str(body.get("saveMode", "copy")) == "overwrite" and route != "/api/media/merge"
            if want_overwrite:
                dreq = urllib.request.Request(
                    f"http://{host['ip']}:{host['port']}/api/file/delete",
                    data=json.dumps({"paths": [raw_path], "recycle": bool(body.get("recycleOriginal", True))}).encode("utf-8"),
                    headers={"X-DeckVault-Token": host["token"], "Content-Type": "application/json"},
                    method="POST",
                )
                urllib.request.urlopen(dreq, timeout=30).close()

            res["path"] = f"{remote_dir}{sep}{out_name}" if remote_dir else out_name
            res["savedBytes"] = max(0, res["oldSizeBytes"] - res["newSizeBytes"]) if want_overwrite else 0
            res["relayedViaLocal"] = True
            return res
        finally:
            shutil.rmtree(stage_dir, ignore_errors=True)

    def record_client_activity(self, ip: str) -> None:
        with self._clients_lock:
            self.active_clients[ip] = time.time()

    def log(self, msg: str) -> None:
        stamp = time.strftime("%H:%M:%S")
        line = f"[{stamp}] {msg}"
        try:
            self.events.put_nowait(line)
        except queue.Full:
            try:
                self.events.get_nowait()
                self.events.put_nowait(line)
            except queue.Empty:
                pass
        print(line, flush=True)


def make_handler(rt: CompanionRuntime):
    """Create the HTTP/1.1 request handler bound to CompanionRuntime."""

    class DeckVaultHandler(BaseHTTPRequestHandler):
        protocol_version = "HTTP/1.1"

        def log_message(self, format: str, *args: Any) -> None:
            # Suppress noisy per-thumbnail stderr logs
            return

        def _send_json(self, status: int, payload: Any) -> None:
            raw = json.dumps(payload, separators=(",", ":")).encode("utf-8")
            self.send_response(status)
            self.send_header("Content-Type", "application/json; charset=utf-8")
            self.send_header("Content-Length", str(len(raw)))
            self.send_header("Cache-Control", "no-store")
            self.end_headers()
            self.wfile.write(raw)

        def _send_file(self, file_path: Path | str, content_type: str | None = None, cache_seconds: int = 0) -> None:
            path_str = str(file_path)
            if not os.path.isfile(path_str):
                self._send_json(HTTPStatus.NOT_FOUND, {"error": "File not found"})
                return

            file_size = os.path.getsize(path_str)
            if not content_type:
                guessed, _ = mimetypes.guess_type(path_str)
                content_type = guessed or "application/octet-stream"

            range_header = self.headers.get("Range")
            start = 0
            end = file_size - 1
            status = HTTPStatus.OK

            if range_header and range_header.startswith("bytes=") and file_size > 0:
                spec = range_header[6:].split(",")[0].strip()
                parts = spec.split("-", 1)
                try:
                    if parts[0]:
                        start = int(parts[0])
                        end = int(parts[1]) if parts[1] else file_size - 1
                    elif parts[1]:
                        suffix = int(parts[1])
                        start = max(0, file_size - suffix)
                        end = file_size - 1
                    start = max(0, min(start, file_size - 1))
                    end = max(start, min(end, file_size - 1))
                    status = HTTPStatus.PARTIAL_CONTENT
                except ValueError:
                    start = 0
                    end = file_size - 1

            length = max(0, (end - start + 1) if file_size > 0 else 0)
            self.send_response(status)
            self.send_header("Content-Type", content_type)
            self.send_header("Accept-Ranges", "bytes")
            self.send_header("Content-Length", str(length))
            if status == HTTPStatus.PARTIAL_CONTENT:
                self.send_header("Content-Range", f"bytes {start}-{end}/{file_size}")
            if cache_seconds > 0:
                self.send_header("Cache-Control", f"public, max-age={cache_seconds}")
            self.end_headers()

            if self.command == "HEAD" or length == 0:
                return

            chunk_size = 256 * 1024
            try:
                with open(path_str, "rb") as f:
                    f.seek(start)
                    remaining = length
                    while remaining > 0:
                        data = f.read(min(chunk_size, remaining))
                        if not data:
                            break
                        self.wfile.write(data)
                        remaining -= len(data)
            except (BrokenPipeError, ConnectionResetError, ConnectionAbortedError):
                return

        def _read_json_body(self) -> dict[str, Any]:
            length = int(self.headers.get("Content-Length", "0"))
            if length <= 0 or length > 2_000_000:
                return {}
            raw = self.rfile.read(length)
            return json.loads(raw.decode("utf-8"))

        def _check_auth(self, qs: dict[str, list[str]]) -> bool:
            token = self.headers.get("X-DeckVault-Token")
            if not token:
                token = (qs.get("token") or [None])[0]
            if token == rt.desktop_token or rt.config.is_valid_token(token):
                rt.record_client_activity(self.client_address[0])
                return True
            self._send_json(HTTPStatus.UNAUTHORIZED, {"error": "Unauthorized"})
            return False

        def _is_loopback(self) -> bool:
            return self.client_address[0] in ("127.0.0.1", "::1", "::ffff:127.0.0.1")

        def do_HEAD(self) -> None:
            self.do_GET()

        def do_GET(self) -> None:
            parsed = urlparse(self.path)
            qs = parse_qs(parsed.query)
            route = parsed.path

            if route in ("/", "/index.html", "/web", "/desktop"):
                raw = DESKTOP_APP_HTML.encode("utf-8")
                self.send_response(HTTPStatus.OK)
                self.send_header("Content-Type", "text/html; charset=utf-8")
                self.send_header("Content-Length", str(len(raw)))
                self.send_header("Cache-Control", "no-store")
                self.end_headers()
                self.wfile.write(raw)
                return

            if route == "/api/desktop/bootstrap":
                if not self._is_loopback():
                    self._send_json(HTTPStatus.FORBIDDEN, {"error": "Desktop bootstrap restricted to localhost"})
                    return
                self._send_json(HTTPStatus.OK, {
                    "token": rt.desktop_token,
                    "localHostId": rt.config.data["host_id"],
                    "deviceName": rt.config.data["device_name"],
                    "pin": rt.config.data["pin"],
                    "ips": get_local_ipv4_addresses(),
                    "fleetHosts": rt.fleet.public_list(),
                    "favorites": rt.favorites.list_all(),
                    "gdrive": detect_gdrive_environment(rt.config.data),
                    "cloudProviders": detect_cloud_storage_providers(rt.config.data),
                    "jobs": rt.jobs.snapshot(),
                })
                return

            if route == "/DeckVault.apk":
                apk_path = Path(r"C:\DeckVault\android\app\build\outputs\apk\release\app-release.apk")
                if not apk_path.is_file():
                    self._send_json(HTTPStatus.NOT_FOUND, {"error": "APK not built yet"})
                    return
                self._send_file(apk_path, "application/vnd.android.package-archive", cache_seconds=0)
                return

            if route == "/api/ping":
                self._send_json(HTTPStatus.OK, {
                    "service": "DeckVault",
                    "version": APP_VERSION,
                    "videoEditOrder": "transform_first",
                    "hostId": rt.config.data["host_id"],
                    "deviceName": rt.config.data["device_name"],
                    "os": "Windows",
                    "ffmpeg": bool(rt.media.ffmpeg),
                    "spaceSavedBytes": int(rt.config.data.get("space_saved_bytes", 0)),
                    "indexer": rt.indexer.status(),
                })
                return

            if not self._check_auth(qs):
                return

            try:
                if route == "/api/roots":
                    self._handle_roots()
                elif route == "/api/tree":
                    self._handle_tree(qs)
                elif route == "/api/list":
                    self._handle_list(qs)
                elif route == "/api/search":
                    self._handle_search(qs)
                elif route == "/api/video/thumbnail":
                    raw_path = (qs.get("path") or [""])[0]
                    width = int((qs.get("w") or ["480"])[0])
                    safe = rt.config.resolve_safe_path(raw_path)
                    thumb = rt.media.get_or_build_thumbnail(safe, width)
                    self._send_file(thumb, "image/jpeg", cache_seconds=86400)
                elif route == "/api/video/storyboard":
                    raw_path = (qs.get("path") or [""])[0]
                    cols = int((qs.get("cols") or ["4"])[0])
                    rows = int((qs.get("rows") or ["4"])[0])
                    safe = rt.config.resolve_safe_path(raw_path)
                    sb_path, _info = rt.media.get_or_build_storyboard(safe, cols, rows)
                    self._send_file(sb_path, "image/jpeg", cache_seconds=86400)
                elif route == "/api/video/storyboard-info":
                    raw_path = (qs.get("path") or [""])[0]
                    cols = int((qs.get("cols") or ["4"])[0])
                    rows = int((qs.get("rows") or ["4"])[0])
                    safe = rt.config.resolve_safe_path(raw_path)
                    _sb_path, info = rt.media.get_or_build_storyboard(safe, cols, rows)
                    self._send_json(HTTPStatus.OK, info)
                elif route == "/api/video/frame":
                    raw_path = (qs.get("path") or [""])[0]
                    ms = int((qs.get("ms") or ["0"])[0])
                    width = int((qs.get("w") or ["640"])[0])
                    safe = rt.config.resolve_safe_path(raw_path)
                    frame = rt.media.extract_frame(safe, ms, width)
                    self._send_file(frame, "image/jpeg", cache_seconds=3600)
                elif route == "/api/image":
                    raw_path = (qs.get("path") or [""])[0]
                    width = int((qs.get("w") or ["0"])[0])
                    safe = rt.config.resolve_safe_path(raw_path)
                    if width > 0 and rt.media.ffmpeg:
                        try:
                            thumb = rt.media.get_or_build_thumbnail(safe, width)
                            self._send_file(thumb, "image/jpeg", cache_seconds=86400)
                        except Exception:
                            self._send_file(safe, cache_seconds=3600)
                    else:
                        self._send_file(safe, cache_seconds=3600)
                elif route == "/api/largest":
                    self._handle_largest(qs)
                elif route == "/api/fleet/discover":
                    devices = discover_all_lan_hosts(
                        rt.config.data["host_id"],
                        int(rt.config.data["http_port"]),
                        int(rt.config.data["udp_port"]),
                    )
                    self._send_json(HTTPStatus.OK, {"devices": devices})
                elif route == "/api/fleet/proxy":
                    self._handle_fleet_proxy(qs, method="GET")
                elif route == "/api/favorites":
                    self._send_json(HTTPStatus.OK, {"favorites": rt.favorites.list_all()})
                elif route == "/api/gdrive/status":
                    self._send_json(HTTPStatus.OK, detect_gdrive_environment(rt.config.data))
                elif route == "/api/cloud/list":
                    self._send_json(HTTPStatus.OK, {"cloudProviders": detect_cloud_storage_providers(rt.config.data)})
                elif route == "/api/jobs":
                    self._send_json(HTTPStatus.OK, rt.jobs.snapshot())
                elif route == "/api/stream/playable":
                    raw_path = (qs.get("path") or [""])[0]
                    start_s = float((qs.get("start") or ["0"])[0] or "0")
                    safe = rt.config.resolve_safe_path(raw_path)
                    self._stream_transcoded_playable(safe, start_s)
                elif route == "/api/stream":
                    raw_path = (qs.get("path") or [""])[0]
                    safe = rt.config.resolve_safe_path(raw_path)
                    self._send_file(safe)
                else:
                    self._send_json(HTTPStatus.NOT_FOUND, {"error": "Unknown route"})
            except PermissionError as exc:
                self._send_json(HTTPStatus.FORBIDDEN, {"error": str(exc)})
            except FileNotFoundError as exc:
                self._send_json(HTTPStatus.NOT_FOUND, {"error": str(exc)})
            except Exception as exc:
                rt.log(f"GET {route} error: {exc}")
                self._send_json(HTTPStatus.INTERNAL_SERVER_ERROR, {"error": str(exc)})

        def do_POST(self) -> None:
            parsed = urlparse(self.path)
            qs = parse_qs(parsed.query)
            route = parsed.path

            if route == "/api/pair":
                body = self._read_json_body()
                pin = str(body.get("pin", ""))
                client_name = str(body.get("clientName", "Samsung Device"))
                token = rt.config.verify_pin_and_issue_token(pin, client_name)
                if not token:
                    rt.log(f"Failed pairing attempt from {self.client_address[0]} ({client_name})")
                    self._send_json(HTTPStatus.UNAUTHORIZED, {"error": "Invalid PIN"})
                    return
                rt.record_client_activity(self.client_address[0])
                rt.log(f"Paired '{client_name}' at {self.client_address[0]}")
                self._send_json(HTTPStatus.OK, {
                    "token": token,
                    "hostId": rt.config.data["host_id"],
                    "deviceName": rt.config.data["device_name"],
                    "os": "Windows",
                    "ffmpeg": bool(rt.media.ffmpeg),
                    "spaceSavedBytes": int(rt.config.data.get("space_saved_bytes", 0)),
                })
                return

            if route == "/api/desktop/focus":
                self._read_json_body()
                if not self._is_loopback():
                    self._send_json(HTTPStatus.FORBIDDEN, {"error": "Localhost only"})
                    return
                focused = _focus_existing_desktop_window()
                if not focused:
                    last_spawn = float(getattr(self.server, "_last_window_spawn_ts", 0.0))
                    if time.time() - last_spawn < 2.5:
                        for _ in range(6):
                            time.sleep(0.25)
                            if _focus_existing_desktop_window():
                                focused = True
                                break
                if not focused:
                    _kill_zombie_window_processes(os.getpid())
                    exe_dir_cmd = [sys.executable, "--window"] if getattr(sys, "frozen", False) else [sys.executable, __file__, "--window"]
                    setattr(self.server, "_last_window_spawn_ts", time.time())
                    subprocess.Popen(exe_dir_cmd, creationflags=SUBPROCESS_FLAGS)
                self._send_json(HTTPStatus.OK, {"focused": focused, "launched": not focused})
                return

            if route == "/api/desktop/quit":
                self._read_json_body()
                if not self._is_loopback():
                    self._send_json(HTTPStatus.FORBIDDEN, {"error": "Localhost only"})
                    return
                self._send_json(HTTPStatus.OK, {"quitting": True})
                def _bye():
                    time.sleep(0.15)
                    _kill_zombie_window_processes(0)
                    os._exit(0)
                t = threading.Thread(target=_bye, daemon=True)
                t.start()
                return

            if not self._check_auth(qs):
                return

            try:
                if route == "/api/file/upload":
                    self._handle_upload(qs)
                    return
                if route == "/api/fleet/proxy":
                    self._handle_fleet_proxy(qs, method="POST")
                    return

                body = self._read_json_body()
                if route == "/api/fleet/pair":
                    ip = str(body.get("ip", "")).strip()
                    port = int(body.get("port", DEFAULT_HTTP_PORT))
                    pin = str(body.get("pin", "")).strip()
                    label = str(body.get("label", "")).strip()
                    if not ip or not pin:
                        raise ValueError("IP and PIN are required")
                    req = urllib.request.Request(
                        f"http://{ip}:{port}/api/pair",
                        data=json.dumps({"pin": pin, "clientName": f"{rt.config.data['device_name']} (Desktop)"}).encode("utf-8"),
                        headers={"Content-Type": "application/json"},
                        method="POST",
                    )
                    with urllib.request.urlopen(req, timeout=8) as resp:
                        pair_data = json.loads(resp.read().decode("utf-8"))
                    host_entry = {
                        "hostId": pair_data["hostId"],
                        "name": pair_data.get("deviceName", ip),
                        "label": label or pair_data.get("deviceName", ip),
                        "ip": ip,
                        "port": port,
                        "token": pair_data["token"],
                        "os": pair_data.get("os", "Device"),
                    }
                    rt.fleet.upsert(host_entry)
                    self._send_json(HTTPStatus.OK, {
                        "host": {k: v for k, v in host_entry.items() if k != "token"},
                        "fleetHosts": rt.fleet.public_list(),
                    })
                    return
                if route == "/api/fleet/remove":
                    hid = str(body.get("hostId", ""))
                    rt.fleet.remove(hid)
                    self._send_json(HTTPStatus.OK, {"fleetHosts": rt.fleet.public_list()})
                    return
                if route == "/api/fleet/relay":
                    self._handle_fleet_relay(body)
                    return

                if route == "/api/rescan":
                    raw_path = body.get("path")
                    target = rt.config.resolve_safe_path(raw_path) if raw_path else None
                    rt.indexer.start_background_scan(target)
                    self._send_json(HTTPStatus.OK, {"started": True, "indexer": rt.indexer.status()})
                elif route == "/api/folder/create":
                    safe_parent = rt.config.resolve_safe_path(str(body.get("parentDir", "")))
                    if not os.path.isdir(safe_parent):
                        raise NotADirectoryError(safe_parent)
                    folder_name = os.path.basename(str(body.get("name", "")).strip())
                    if not folder_name or folder_name in (".", ".."):
                        raise ValueError("Invalid folder name")
                    new_dir = os.path.join(safe_parent, folder_name)
                    rt.config.resolve_safe_path(new_dir)
                    os.makedirs(new_dir, exist_ok=True)
                    rt.log(f"Created folder: {new_dir}")
                    self._send_json(HTTPStatus.OK, {"ok": True, "path": new_dir, "name": folder_name})
                elif route == "/api/file/transfer":
                    self._handle_local_transfer(body)
                elif route == "/api/file/pull":
                    self._handle_remote_pull(body)
                elif route == "/api/video/trim":
                    safe = rt.config.resolve_safe_path(str(body.get("path", "")))
                    start_ms = int(body.get("startMs", 0))
                    end_ms = int(body.get("endMs", 0))
                    mode = str(body.get("mode", "lossless"))
                    crf = int(body.get("crf", 23))
                    recycle = bool(body.get("recycleOriginal", True))
                    rt.log(f"Trimming ({mode}) {os.path.basename(safe)} [{start_ms}ms..{end_ms}ms]")
                    result = rt.media.trim_and_replace(safe, start_ms, end_ms, mode, crf, recycle)
                    rt.indexer.adjust_ancestors(safe, result["sizeDeltaBytes"], 0, 0)
                    total_saved = rt.config.add_space_saved(result["savedBytes"])
                    result["totalSpaceSavedBytes"] = total_saved
                    rt.log(
                        f"Saved over {os.path.basename(safe)}: "
                        f"reclaimed {result['savedBytes'] / (1024*1024):.1f} MB"
                    )
                    self._send_json(HTTPStatus.OK, result)
                elif route == "/api/video/edit":
                    result = rt.run_or_relay_media_op("/api/video/edit", body)
                    self._send_json(HTTPStatus.OK, result)
                elif route == "/api/video/snapshot":
                    safe = rt.config.resolve_safe_path(str(body.get("path", "")))
                    ms = int(body.get("ms", 0))
                    snap_path = rt.media.save_snapshot(safe, ms)
                    self._send_json(HTTPStatus.OK, {"ok": True, "path": snap_path, "name": os.path.basename(snap_path)})
                elif route == "/api/system/reveal":
                    if not self._is_loopback():
                        raise PermissionError("Localhost only")
                    safe = rt.config.resolve_safe_path(str(body.get("path", "")))
                    subprocess.Popen(["explorer", f"/select,{safe}"], creationflags=SUBPROCESS_FLAGS)
                    self._send_json(HTTPStatus.OK, {"ok": True})
                elif route == "/api/system/open":
                    if not self._is_loopback():
                        raise PermissionError("Localhost only")
                    safe = rt.config.resolve_safe_path(str(body.get("path", "")))
                    vlc_candidates = [
                        r"C:\Program Files\VideoLAN\VLC\vlc.exe",
                        r"C:\Program Files (x86)\VideoLAN\VLC\vlc.exe",
                    ]
                    vlc = next((c for c in vlc_candidates if os.path.isfile(c)), None)
                    if vlc:
                        subprocess.Popen([vlc, safe], creationflags=SUBPROCESS_FLAGS)
                        self._send_json(HTTPStatus.OK, {"ok": True, "player": "VLC"})
                    else:
                        os.startfile(safe)
                        self._send_json(HTTPStatus.OK, {"ok": True, "player": "System Default"})
                elif route == "/api/favorites/toggle":
                    added, favs = rt.favorites.toggle(body)
                    self._send_json(HTTPStatus.OK, {"added": added, "favorites": favs})
                elif route == "/api/gdrive/configure":
                    if "savedDir" in body:
                        rt.config.data["gdrive_backup_dir"] = str(body.get("savedDir", "")).strip()
                    if "savedRemote" in body:
                        rt.config.data["gdrive_rclone_remote"] = str(body.get("savedRemote", "")).strip()
                    rt.config.save()
                    self._send_json(HTTPStatus.OK, detect_gdrive_environment(rt.config.data))
                elif route == "/api/gdrive/backup":
                    self._handle_gdrive_backup(body)
                elif route == "/api/desktop/pin/new":
                    if not self._is_loopback():
                        raise PermissionError("Localhost only")
                    new_pin = rt.config.rotate_pin()
                    rt.log(f"Generated new PIN: {new_pin}")
                    self._send_json(HTTPStatus.OK, {"pin": new_pin})
                elif route == "/api/desktop/firewall":
                    if not self._is_loopback():
                        raise PermissionError("Localhost only")
                    ok_fw, msg_fw = add_windows_firewall_rule(int(rt.config.data["http_port"]), int(rt.config.data["udp_port"]))
                    self._send_json(HTTPStatus.OK, {"ok": ok_fw, "message": msg_fw})
                elif route == "/api/file/rename":
                    safe = rt.config.resolve_safe_path(str(body.get("path", "")))
                    new_name = os.path.basename(str(body.get("newName", "")).strip())
                    if not new_name:
                        raise ValueError("newName cannot be empty")
                    target = os.path.join(os.path.dirname(safe), new_name)
                    rt.config.resolve_safe_path(target)
                    os.replace(safe, target)
                    rt.media.invalidate_media_caches(safe)
                    self._send_json(HTTPStatus.OK, {"ok": True, "oldPath": safe, "newPath": target, "name": new_name})
                elif route == "/api/cloud/connect":
                    raw_path = str(body.get("path", "")).strip()
                    name = str(body.get("name", "")).strip()
                    provider = str(body.get("provider", "custom")).strip() or "custom"
                    if not raw_path:
                        raise ValueError("Folder or drive path is required")
                    norm = os.path.normpath(raw_path)
                    if len(norm) == 2 and norm[1] == ":":
                        norm += "\\"
                    if not os.path.isdir(norm):
                        raise NotADirectoryError(f"Folder not found: {norm}")
                    if not name:
                        name = os.path.basename(norm.rstrip("\\/")) or norm
                    list_p = list(rt.config.data.get("cloud_providers", []) or [])
                    list_p = [x for x in list_p if os.path.normpath(str(x.get("path", ""))).lower() != norm.lower()]
                    list_p.insert(0, {
                        "id": f"cloud_{uuid.uuid4().hex[:8]}",
                        "name": name,
                        "provider": provider,
                        "path": norm,
                    })
                    rt.config.data["cloud_providers"] = list_p
                    roots_cur = list(rt.config.data.get("roots", []) or [])
                    if not any(os.path.normpath(r).lower() == norm.lower() for r in roots_cur):
                        roots_cur.append(norm)
                        rt.config.data["roots"] = roots_cur
                    rt.config.save()
                    self._send_json(HTTPStatus.OK, {"cloudProviders": detect_cloud_storage_providers(rt.config.data)})
                elif route == "/api/cloud/remove":
                    cid = str(body.get("id", ""))
                    list_p = [x for x in (rt.config.data.get("cloud_providers", []) or []) if str(x.get("id", "")) != cid]
                    rt.config.data["cloud_providers"] = list_p
                    rt.config.save()
                    self._send_json(HTTPStatus.OK, {"cloudProviders": detect_cloud_storage_providers(rt.config.data)})
                elif route in (
                    "/api/media/compress-estimate",
                    "/api/media/compress",
                    "/api/media/upscale",
                    "/api/media/extract-audio",
                    "/api/media/gif",
                    "/api/media/merge",
                ):
                    res = rt.run_or_relay_media_op(route, body)
                    self._send_json(HTTPStatus.OK, res)
                elif route == "/api/jobs/enqueue":
                    job_type = str(body.get("type", "")).strip()
                    batch = body.get("batch")
                    if isinstance(batch, list) and batch:
                        created = [rt.jobs.enqueue(job_type, item) for item in batch]
                        self._send_json(HTTPStatus.OK, {"ok": True, "jobs": created, "snapshot": rt.jobs.snapshot()})
                    else:
                        job = rt.jobs.enqueue(job_type, dict(body.get("payload") or body))
                        self._send_json(HTTPStatus.OK, {"ok": True, "job": job, "snapshot": rt.jobs.snapshot()})
                elif route == "/api/jobs/cancel":
                    job_id = str(body.get("id", "")).strip()
                    job = rt.jobs.cancel(job_id)
                    self._send_json(HTTPStatus.OK, {"ok": True, "job": job, "snapshot": rt.jobs.snapshot()})
                elif route == "/api/jobs/clear":
                    removed = rt.jobs.clear_finished()
                    self._send_json(HTTPStatus.OK, {"ok": True, "removed": removed, "snapshot": rt.jobs.snapshot()})
                elif route == "/api/system/prepare-drag":
                    if not self._is_loopback():
                        raise PermissionError("Localhost only")
                    host_id = str(body.get("hostId", rt.config.data["host_id"]))
                    paths = [str(p) for p in (body.get("paths") or []) if str(p).strip()]
                    local_paths = _prepare_local_drag_files(rt, host_id, paths)
                    self._send_json(HTTPStatus.OK, {"ok": True, "localPaths": local_paths})
                elif route == "/api/system/copy-files-clipboard":
                    if not self._is_loopback():
                        raise PermissionError("Localhost only")
                    host_id = str(body.get("hostId", rt.config.data["host_id"]))
                    paths = [str(p) for p in (body.get("paths") or []) if str(p).strip()]
                    local_paths = _prepare_local_drag_files(rt, host_id, paths)
                    if not local_paths:
                        raise FileNotFoundError("No files found to copy")
                    _set_windows_file_clipboard(local_paths)
                    self._send_json(HTTPStatus.OK, {"ok": True, "count": len(local_paths), "localPaths": local_paths})
                elif route == "/api/file/delete":
                    paths = body.get("paths") or []
                    recycle = bool(body.get("recycle", True))
                    freed = 0
                    deleted = 0
                    errors: list[str] = []
                    for raw_p in paths:
                        raw_str = str(raw_p or "").strip()
                        if not raw_str:
                            continue
                        try:
                            safe = rt.config.resolve_safe_path(raw_str)
                            if not os.path.exists(safe):
                                errors.append(f"{os.path.basename(safe) or safe}: File no longer exists")
                                continue
                            if os.path.isfile(safe):
                                sz = os.path.getsize(safe)
                                is_media = 1 if classify_media_type(safe) in ("video", "image") else 0
                                rt.media.invalidate_media_caches(safe)
                                last_err: Exception | None = None
                                for attempt in range(3):
                                    try:
                                        if recycle:
                                            send_to_recycle_bin(safe)
                                        else:
                                            os.remove(safe)
                                        last_err = None
                                        break
                                    except Exception as exc:
                                        last_err = exc
                                        if attempt < 2:
                                            gc.collect()
                                            time.sleep(0.15)
                                if last_err is not None:
                                    raise last_err
                                rt.indexer.adjust_ancestors(safe, -sz, -1, -is_media)
                                freed += sz
                                deleted += 1
                            elif os.path.isdir(safe):
                                dir_stats = rt.indexer.get_dir_stats(safe) or {"size": 0, "files": 0, "media": 0}
                                last_err = None
                                for attempt in range(3):
                                    try:
                                        if recycle:
                                            send_to_recycle_bin(safe)
                                        else:
                                            shutil.rmtree(safe)
                                        last_err = None
                                        break
                                    except Exception as exc:
                                        last_err = exc
                                        if attempt < 2:
                                            gc.collect()
                                            time.sleep(0.15)
                                if last_err is not None:
                                    raise last_err
                                rt.indexer.adjust_ancestors(
                                    safe, -dir_stats["size"], -dir_stats["files"], -dir_stats["media"]
                                )
                                freed += dir_stats["size"]
                                deleted += 1
                            else:
                                errors.append(f"{safe}: Unsupported item type")
                        except Exception as item_exc:
                            errors.append(f"{os.path.basename(raw_str) or raw_str}: {item_exc}")
                    total_saved = rt.config.add_space_saved(freed)
                    self._send_json(HTTPStatus.OK, {
                        "deletedCount": deleted,
                        "freedBytes": freed,
                        "totalSpaceSavedBytes": total_saved,
                        "errors": errors,
                    })
                else:
                    self._send_json(HTTPStatus.NOT_FOUND, {"error": "Unknown route"})
            except PermissionError as exc:
                self._send_json(HTTPStatus.FORBIDDEN, {"error": str(exc)})
            except Exception as exc:
                rt.log(f"POST {route} error: {exc}")
                self._send_json(HTTPStatus.BAD_REQUEST, {"error": str(exc)})

        def _handle_fleet_proxy(self, qs: dict[str, list[str]], method: str) -> None:
            host_id = (qs.get("hostId") or [""])[0]
            sub_route = (qs.get("route") or [""])[0]
            host = rt.fleet.get(host_id)
            if not host:
                self._send_json(HTTPStatus.NOT_FOUND, {"error": "Paired device not found"})
                return
            if not sub_route.startswith("/api/"):
                self._send_json(HTTPStatus.FORBIDDEN, {"error": "Only /api/* routes allowed"})
                return

            target_url = f"http://{host['ip']}:{host['port']}{sub_route}"
            headers = {"X-DeckVault-Token": host["token"]}
            rng = self.headers.get("Range")
            if rng:
                headers["Range"] = rng
            ctype = self.headers.get("Content-Type")
            if ctype:
                headers["Content-Type"] = ctype

            req_body = None
            if method == "POST":
                clen = int(self.headers.get("Content-Length", "0"))
                if clen > 0:
                    req_body = self.rfile.read(clen)

            req = urllib.request.Request(target_url, data=req_body, headers=headers, method=method)
            try:
                with urllib.request.urlopen(req, timeout=120) as resp:
                    self.send_response(resp.status)
                    for hname in ("Content-Type", "Content-Length", "Content-Range", "Accept-Ranges", "Cache-Control"):
                        val = resp.headers.get(hname)
                        if val is not None:
                            self.send_header(hname, val)
                    self.end_headers()
                    if self.command != "HEAD":
                        while True:
                            chunk = resp.read(128 * 1024)
                            if not chunk:
                                break
                            try:
                                self.wfile.write(chunk)
                            except (BrokenPipeError, ConnectionResetError, ConnectionAbortedError):
                                break
            except urllib.error.HTTPError as err:
                raw = err.read()
                self.send_response(err.code)
                self.send_header("Content-Type", err.headers.get("Content-Type", "application/json"))
                self.send_header("Content-Length", str(len(raw)))
                self.end_headers()
                self.wfile.write(raw)

        def _handle_fleet_relay(self, body: dict[str, Any]) -> None:
            local_id = rt.config.data["host_id"]
            src_id = str(body.get("sourceHostId", ""))
            dst_id = str(body.get("targetHostId", ""))
            paths = [str(p) for p in (body.get("paths") or [])]
            dest_dir = str(body.get("destinationDir", ""))
            move = bool(body.get("move", False))

            src_host = None if src_id == local_id else rt.fleet.get(src_id)
            dst_host = None if dst_id == local_id else rt.fleet.get(dst_id)
            if (src_id != local_id and not src_host) or (dst_id != local_id and not dst_host):
                raise ValueError("Source or destination device is not paired")

            transferred = 0
            for src_path in paths:
                fname = os.path.basename(src_path.rstrip("\\/")) or f"file_{int(time.time())}"
                # Read stream bytes (from local PC or remote host)
                if src_host is None:
                    safe_src = rt.config.resolve_safe_path(src_path)
                    with open(safe_src, "rb") as f:
                        data = f.read()
                else:
                    q = url_quote(src_path, safe="")
                    sreq = urllib.request.Request(
                        f"http://{src_host['ip']}:{src_host['port']}/api/stream?path={q}",
                        headers={"X-DeckVault-Token": src_host["token"]},
                        method="GET",
                    )
                    with urllib.request.urlopen(sreq, timeout=600) as resp:
                        data = resp.read()

                # Write to destination (local PC or remote host)
                if dst_host is None:
                    safe_dest = rt.config.resolve_safe_path(dest_dir)
                    target = _resolve_unique_dest(safe_dest, fname, "rename")
                    if target:
                        with open(target, "wb") as out:
                            out.write(data)
                else:
                    up_url = (
                        f"http://{dst_host['ip']}:{dst_host['port']}/api/file/upload"
                        f"?dir={url_quote(dest_dir, safe='')}&name={url_quote(fname, safe='')}&conflict=rename"
                    )
                    ureq = urllib.request.Request(
                        up_url,
                        data=data,
                        headers={"X-DeckVault-Token": dst_host["token"], "Content-Type": "application/octet-stream"},
                        method="POST",
                    )
                    with urllib.request.urlopen(ureq, timeout=600) as resp:
                        if resp.status >= 400:
                            raise OSError(f"Upload failed: HTTP {resp.status}")

                if move:
                    if src_host is None:
                        send_to_recycle_bin(rt.config.resolve_safe_path(src_path))
                    else:
                        dreq = urllib.request.Request(
                            f"http://{src_host['ip']}:{src_host['port']}/api/file/delete",
                            data=json.dumps({"paths": [src_path], "recycle": True}).encode("utf-8"),
                            headers={"X-DeckVault-Token": src_host["token"], "Content-Type": "application/json"},
                            method="POST",
                        )
                        urllib.request.urlopen(dreq, timeout=30).close()
                transferred += 1

            self._send_json(HTTPStatus.OK, {"transferredCount": transferred})

        def _stream_transcoded_playable(self, file_path: str, start_s: float = 0.0) -> None:
            if not rt.media.ffmpeg:
                self._send_json(HTTPStatus.INTERNAL_SERVER_ERROR, {"error": "FFmpeg required for live transcode"})
                return
            if not os.path.isfile(file_path):
                self._send_json(HTTPStatus.NOT_FOUND, {"error": "File not found"})
                return
            if self.command == "HEAD":
                self.send_response(HTTPStatus.OK)
                self.send_header("Content-Type", "video/mp4")
                self.end_headers()
                return

            cmd = [rt.media.ffmpeg, "-y"]
            if start_s > 0.05:
                cmd.extend(["-ss", f"{start_s:.3f}"])
            cmd.extend([
                "-i", file_path,
                "-map", "0:v:0", "-map", "0:a?",
                "-vf", "scale=trunc(iw/2)*2:trunc(ih/2)*2,fps=60",
                "-c:v", "libx264", "-preset", "ultrafast", "-tune", "zerolatency", "-crf", "22", "-pix_fmt", "yuv420p",
                "-c:a", "aac", "-b:a", "192k", "-ac", "2",
                "-f", "mp4", "-movflags", "frag_keyframe+empty_moov+default_base_moof",
                "pipe:1",
            ])
            proc = subprocess.Popen(
                cmd,
                stdout=subprocess.PIPE,
                stderr=subprocess.DEVNULL,
                bufsize=64 * 1024,
                creationflags=SUBPROCESS_FLAGS,
            )
            self.send_response(HTTPStatus.OK)
            self.send_header("Content-Type", "video/mp4")
            self.send_header("Transfer-Encoding", "chunked")
            self.send_header("Cache-Control", "no-store")
            self.end_headers()
            try:
                assert proc.stdout is not None
                while True:
                    chunk = proc.stdout.read(64 * 1024)
                    if not chunk:
                        break
                    self.wfile.write(f"{len(chunk):X}\r\n".encode("ascii") + chunk + b"\r\n")
                self.wfile.write(b"0\r\n\r\n")
                self.wfile.flush()
            except (BrokenPipeError, ConnectionResetError, ConnectionAbortedError, OSError):
                pass
            finally:
                try:
                    proc.terminate()
                    proc.wait(timeout=2)
                except Exception:
                    try:
                        proc.kill()
                    except Exception:
                        pass

        def _handle_gdrive_backup(self, body: dict[str, Any]) -> None:
            env = detect_gdrive_environment(rt.config.data)
            target_dir = str(body.get("targetDir") or env.get("savedDir") or "").strip()
            target_remote = str(body.get("targetRemote") or env.get("savedRemote") or "").strip()
            src_host_id = str(body.get("sourceHostId") or rt.config.data["host_id"])
            paths = [str(p) for p in (body.get("paths") or [])]
            if not paths:
                raise ValueError("No files selected for backup")

            local_id = rt.config.data["host_id"]
            src_host = None if src_host_id == local_id else rt.fleet.get(src_host_id)
            if src_host_id != local_id and not src_host:
                raise ValueError("Source device not paired")

            if not target_dir and not target_remote:
                raise ValueError("No Google Drive folder or rclone remote configured yet")

            backed_up = 0
            if target_dir:
                os.makedirs(target_dir, exist_ok=True)
                for p in paths:
                    fname = os.path.basename(p.rstrip("\\/")) or f"backup_{int(time.time())}"
                    dst = _resolve_unique_dest(target_dir, fname, "rename")
                    if not dst:
                        continue
                    if src_host is None:
                        safe_src = rt.config.resolve_safe_path(p)
                        _safe_copy_or_move_item(safe_src, dst, move=False)
                    else:
                        q = url_quote(p, safe="")
                        sreq = urllib.request.Request(
                            f"http://{src_host['ip']}:{src_host['port']}/api/stream?path={q}",
                            headers={"X-DeckVault-Token": src_host["token"]},
                        )
                        with urllib.request.urlopen(sreq, timeout=600) as resp, open(dst, "wb") as out:
                            while True:
                                chunk = resp.read(128 * 1024)
                                if not chunk:
                                    break
                                out.write(chunk)
                    backed_up += 1
                rt.log(f"Backed up {backed_up} item(s) to Google Drive folder: {target_dir}")
                self._send_json(HTTPStatus.OK, {"backedUpCount": backed_up, "destination": target_dir})
                return

            rclone_exe = shutil.which("rclone")
            if not rclone_exe:
                raise RuntimeError("rclone not found on PATH")
            remote_dest = target_remote.rstrip("/") + "/DeckVault Backup"
            import tempfile
            for p in paths:
                fname = os.path.basename(p.rstrip("\\/")) or f"backup_{int(time.time())}"
                if src_host is None:
                    safe_src = rt.config.resolve_safe_path(p)
                    proc = subprocess.run([rclone_exe, "copy", safe_src, remote_dest], capture_output=True, text=True, timeout=600, creationflags=SUBPROCESS_FLAGS)
                    if proc.returncode != 0:
                        raise RuntimeError(proc.stderr[-300:] or "rclone upload failed")
                else:
                    with tempfile.TemporaryDirectory() as td:
                        tmp_f = os.path.join(td, fname)
                        q = url_quote(p, safe="")
                        sreq = urllib.request.Request(
                            f"http://{src_host['ip']}:{src_host['port']}/api/stream?path={q}",
                            headers={"X-DeckVault-Token": src_host["token"]},
                        )
                        with urllib.request.urlopen(sreq, timeout=600) as resp, open(tmp_f, "wb") as out:
                            shutil.copyfileobj(resp, out)
                        proc = subprocess.run([rclone_exe, "copy", tmp_f, remote_dest], capture_output=True, text=True, timeout=600, creationflags=SUBPROCESS_FLAGS)
                        if proc.returncode != 0:
                            raise RuntimeError(proc.stderr[-300:] or "rclone upload failed")
                backed_up += 1
            rt.log(f"Backed up {backed_up} item(s) to rclone remote: {remote_dest}")
            self._send_json(HTTPStatus.OK, {"backedUpCount": backed_up, "destination": remote_dest})

        def _handle_upload(self, qs: dict[str, list[str]]) -> None:
            raw_dir = (qs.get("dir") or [""])[0]
            raw_name = (qs.get("name") or [self.headers.get("X-File-Name", "")])[0]
            conflict = (qs.get("conflict") or ["rename"])[0]
            safe_dir = rt.config.resolve_safe_path(raw_dir)
            if not os.path.isdir(safe_dir):
                raise NotADirectoryError(f"Destination is not a directory: {safe_dir}")

            target = _resolve_unique_dest(safe_dir, raw_name, conflict)
            if target is None:
                self._send_json(HTTPStatus.OK, {"ok": True, "skipped": True, "path": os.path.join(safe_dir, os.path.basename(raw_name)), "sizeBytes": 0})
                return

            rt.config.resolve_safe_path(target)
            content_length = int(self.headers.get("Content-Length", "0"))
            tmp_path = os.path.join(safe_dir, f".deckvault_upload_{uuid.uuid4().hex}")
            written = 0
            try:
                remaining = content_length
                chunk_size = 256 * 1024
                with open(tmp_path, "wb") as out:
                    while remaining > 0:
                        data = self.rfile.read(min(chunk_size, remaining))
                        if not data:
                            break
                        out.write(data)
                        written += len(data)
                        remaining -= len(data)
                if content_length > 0 and written < content_length:
                    raise OSError(f"Incomplete upload ({written}/{content_length} bytes)")
                os.replace(tmp_path, target)
            except Exception:
                if os.path.exists(tmp_path):
                    try:
                        os.remove(tmp_path)
                    except OSError:
                        pass
                raise

            is_media = 1 if classify_media_type(target) in ("video", "image") else 0
            rt.indexer.adjust_ancestors(target, written, 1, is_media)
            rt.log(f"Uploaded {os.path.basename(target)} ({written / (1024*1024):.1f} MB) -> {safe_dir}")
            self._send_json(HTTPStatus.OK, {
                "ok": True,
                "path": target,
                "name": os.path.basename(target),
                "sizeBytes": written,
            })

        def _handle_local_transfer(self, body: dict[str, Any]) -> None:
            paths = body.get("paths") or []
            dest_dir = rt.config.resolve_safe_path(str(body.get("destinationDir", "")))
            mode = str(body.get("mode", "move")).lower()
            move = mode != "copy"
            conflict = str(body.get("conflict", "rename")).lower()

            if not os.path.isdir(dest_dir):
                raise NotADirectoryError(f"Destination is not a directory: {dest_dir}")

            transferred: list[dict[str, Any]] = []
            total_bytes = 0
            errors: list[str] = []

            for raw_p in paths:
                try:
                    safe_src = rt.config.resolve_safe_path(str(raw_p))
                    if not os.path.exists(safe_src):
                        raise FileNotFoundError(safe_src)

                    src_norm = os.path.normcase(os.path.abspath(safe_src))
                    dst_dir_norm = os.path.normcase(os.path.abspath(dest_dir))
                    if os.path.isdir(safe_src):
                        if dst_dir_norm == src_norm or dst_dir_norm.startswith(src_norm + os.sep):
                            raise ValueError(f"Cannot {mode} folder into itself: {safe_src}")

                    if os.path.normcase(os.path.abspath(os.path.dirname(safe_src))) == dst_dir_norm:
                        if move:
                            continue

                    target = _resolve_unique_dest(dest_dir, os.path.basename(safe_src), conflict)
                    if target is None:
                        continue
                    rt.config.resolve_safe_path(target)

                    if os.path.isdir(safe_src):
                        sz, fcount, mcount = _count_tree_stats(safe_src)
                    else:
                        sz = os.path.getsize(safe_src)
                        fcount = 1
                        mcount = 1 if classify_media_type(safe_src) in ("video", "image") else 0

                    _safe_copy_or_move_item(safe_src, target, move=move)
                    if move:
                        rt.media.invalidate_media_caches(safe_src)
                        rt.indexer.adjust_ancestors(safe_src, -sz, -fcount, -mcount)
                    rt.indexer.adjust_ancestors(target, sz, fcount, mcount)

                    total_bytes += sz
                    transferred.append({"src": safe_src, "dest": target, "sizeBytes": sz})
                except Exception as item_exc:
                    errors.append(f"{raw_p}: {item_exc}")

            verb = "Moved" if move else "Copied"
            rt.log(f"{verb} {len(transferred)} item(s) -> {dest_dir}")
            self._send_json(HTTPStatus.OK, {
                "transferredCount": len(transferred),
                "transferredBytes": total_bytes,
                "results": transferred,
                "errors": errors,
            })

        def _handle_remote_pull(self, body: dict[str, Any]) -> None:
            source_base = str(body.get("sourceBaseUrl", "")).rstrip("/")
            source_token = str(body.get("sourceToken", ""))
            paths = body.get("paths") or []
            dest_dir = rt.config.resolve_safe_path(str(body.get("destinationDir", "")))
            conflict = str(body.get("conflict", "rename")).lower()

            if not source_base or not source_token:
                raise ValueError("sourceBaseUrl and sourceToken are required")
            if not os.path.isdir(dest_dir):
                raise NotADirectoryError(f"Destination is not a directory: {dest_dir}")

            transferred: list[dict[str, Any]] = []
            total_bytes = 0
            errors: list[str] = []

            for raw_p in paths:
                src_path = str(raw_p)
                tmp_path = os.path.join(dest_dir, f".deckvault_pull_{uuid.uuid4().hex}")
                try:
                    stream_url = f"{source_base}/api/stream?path={url_quote(src_path)}"
                    req = urllib.request.Request(stream_url, headers={"X-DeckVault-Token": source_token})
                    target = _resolve_unique_dest(dest_dir, os.path.basename(src_path), conflict)
                    if target is None:
                        continue
                    rt.config.resolve_safe_path(target)

                    written = 0
                    with urllib.request.urlopen(req, timeout=30) as resp, open(tmp_path, "wb") as out:
                        while True:
                            chunk = resp.read(256 * 1024)
                            if not chunk:
                                break
                            out.write(chunk)
                            written += len(chunk)

                    os.replace(tmp_path, target)
                    is_media = 1 if classify_media_type(target) in ("video", "image") else 0
                    rt.indexer.adjust_ancestors(target, written, 1, is_media)
                    total_bytes += written
                    transferred.append({"src": src_path, "dest": target, "sizeBytes": written})
                except Exception as item_exc:
                    if os.path.exists(tmp_path):
                        try:
                            os.remove(tmp_path)
                        except OSError:
                            pass
                    errors.append(f"{src_path}: {item_exc}")

            rt.log(f"Pulled {len(transferred)} remote file(s) -> {dest_dir}")
            self._send_json(HTTPStatus.OK, {
                "transferredCount": len(transferred),
                "transferredBytes": total_bytes,
                "results": transferred,
                "errors": errors,
            })

        def _handle_roots(self) -> None:
            roots_payload = []
            quick_folders = []
            user_profile = str(Path.home())
            for label, sub in [
                ("Desktop", "Desktop"),
                ("Downloads", "Downloads"),
                ("Videos", "Videos"),
                ("Pictures", "Pictures"),
                ("Documents", "Documents"),
            ]:
                p = os.path.join(user_profile, sub)
                if os.path.isdir(p):
                    stats = rt.indexer.get_dir_stats(p)
                    quick_folders.append({
                        "name": label,
                        "path": p,
                        "sizeBytes": stats["size"] if stats else -1,
                        "fileCount": stats["files"] if stats else -1,
                        "mediaCount": stats["media"] if stats else -1,
                    })

            for root in rt.config.get_roots():
                try:
                    usage = shutil.disk_usage(root)
                    total_b, used_b, free_b = usage.total, usage.used, usage.free
                except OSError:
                    total_b, used_b, free_b = 0, 0, 0
                stats = rt.indexer.get_dir_stats(root)
                label = get_drive_volume_label(root)
                display = f"{root[:2]} ({label})" if label else root[:2]
                roots_payload.append({
                    "name": display,
                    "path": root,
                    "totalBytes": total_b,
                    "usedBytes": used_b,
                    "freeBytes": free_b,
                    "indexedSizeBytes": stats["size"] if stats else -1,
                    "fileCount": stats["files"] if stats else -1,
                    "mediaCount": stats["media"] if stats else -1,
                })

            self._send_json(HTTPStatus.OK, {
                "deviceName": rt.config.data["device_name"],
                "hostId": rt.config.data["host_id"],
                "os": "Windows",
                "ffmpeg": bool(rt.media.ffmpeg),
                "spaceSavedBytes": int(rt.config.data.get("space_saved_bytes", 0)),
                "indexer": rt.indexer.status(),
                "drives": roots_payload,
                "quickFolders": quick_folders,
                "cloudProviders": detect_cloud_storage_providers(rt.config.data),
            })

        def _handle_tree(self, qs: dict[str, list[str]]) -> None:
            raw_path = (qs.get("path") or [""])[0]
            sort_by = (qs.get("sort") or ["size"])[0]
            safe = rt.config.resolve_safe_path(raw_path)
            if not os.path.isdir(safe):
                raise FileNotFoundError(safe)

            parent_stats = rt.indexer.compute_immediate_if_missing(safe)
            parent_size = max(1, parent_stats["size"])
            children = []
            try:
                with os.scandir(safe) as it:
                    for entry in it:
                        if entry.name in DiskIndexer.SKIP_NAMES:
                            continue
                        try:
                            if entry.is_dir(follow_symlinks=False):
                                c_stats = rt.indexer.get_dir_stats(entry.path)
                                sz = c_stats["size"] if c_stats else -1
                                pct = round((sz / parent_size) * 100.0, 1) if sz >= 0 else 0.0
                                has_sub = False
                                try:
                                    with os.scandir(entry.path) as sub_it:
                                        has_sub = any(
                                            s.is_dir(follow_symlinks=False)
                                            for s in sub_it
                                            if s.name not in DiskIndexer.SKIP_NAMES
                                        )
                                except OSError:
                                    pass
                                children.append({
                                    "name": entry.name,
                                    "path": entry.path,
                                    "sizeBytes": sz,
                                    "percentOfParent": min(100.0, pct),
                                    "fileCount": c_stats["files"] if c_stats else -1,
                                    "mediaCount": c_stats["media"] if c_stats else -1,
                                    "hasSubfolders": has_sub,
                                })
                        except OSError:
                            continue
            except OSError:
                pass

            if sort_by == "name":
                children.sort(key=lambda d: d["name"].lower())
            else:
                children.sort(key=lambda d: (d["sizeBytes"], d["name"].lower()), reverse=True)

            self._send_json(HTTPStatus.OK, {
                "path": safe,
                "sizeBytes": parent_stats["size"],
                "children": children,
            })

        def _handle_list(self, qs: dict[str, list[str]]) -> None:
            raw_path = (qs.get("path") or [""])[0]
            sort_by = (qs.get("sort") or ["size"])[0]
            order = (qs.get("order") or ["desc"])[0]
            media_filter = (qs.get("filter") or ["all"])[0]
            query = (qs.get("q") or [""])[0].strip().lower()

            safe = rt.config.resolve_safe_path(raw_path)
            if not os.path.isdir(safe):
                raise FileNotFoundError(safe)

            dir_stats = rt.indexer.compute_immediate_if_missing(safe)
            parent_size = max(1, dir_stats["size"])
            drive_root = os.path.splitdrive(safe)[0] + "\\"
            try:
                usage = shutil.disk_usage(drive_root if os.path.exists(drive_root) else safe)
                disk_free = usage.free
                disk_total = usage.total
            except OSError:
                disk_free = 0
                disk_total = 0

            folders: list[dict[str, Any]] = []
            files: list[dict[str, Any]] = []
            uncached_videos: list[tuple[str, os.stat_result]] = []

            try:
                with os.scandir(safe) as it:
                    for entry in it:
                        name = entry.name
                        if name in DiskIndexer.SKIP_NAMES:
                            continue
                        if query and query not in name.lower():
                            continue
                        try:
                            if entry.is_dir(follow_symlinks=False):
                                c_stats = rt.indexer.get_dir_stats(entry.path)
                                sz = c_stats["size"] if c_stats else -1
                                pct = round((sz / parent_size) * 100.0, 1) if sz >= 0 else 0.0
                                folders.append({
                                    "name": name,
                                    "path": entry.path,
                                    "sizeBytes": sz,
                                    "percentOfParent": min(100.0, pct),
                                    "fileCount": c_stats["files"] if c_stats else -1,
                                    "mediaCount": c_stats["media"] if c_stats else -1,
                                })
                            elif entry.is_file(follow_symlinks=False):
                                mtype = classify_media_type(name)
                                st = entry.stat(follow_symlinks=False)
                                if media_filter == "video" and mtype != "video":
                                    continue
                                if media_filter == "image" and mtype != "image":
                                    continue
                                if media_filter == "media" and mtype not in ("video", "image"):
                                    continue
                                if media_filter == "large" and st.st_size < 100 * 1024 * 1024:
                                    continue

                                cached_meta = None
                                if mtype == "video":
                                    cached_meta = rt.cache.get(entry.path, st.st_mtime_ns, st.st_size)
                                    if cached_meta is None and len(uncached_videos) < 40:
                                        uncached_videos.append((entry.path, st))

                                files.append({
                                    "name": name,
                                    "path": entry.path,
                                    "sizeBytes": st.st_size,
                                    "modifiedMs": int(st.st_mtime * 1000),
                                    "mediaType": mtype,
                                    "extension": os.path.splitext(name)[1].lower().lstrip("."),
                                    "meta": cached_meta,
                                })
                        except OSError:
                            continue
            except OSError as exc:
                raise PermissionError(str(exc))

            # Probe up to 16 uncached videos in this directory quickly in parallel (max ~1.2s)
            if uncached_videos and rt.media.ffprobe:
                batch = uncached_videos[:16]
                futures = {
                    rt.media._probe_pool.submit(rt.media.probe_video, vp, vst): vp
                    for vp, vst in batch
                }
                probed_map: dict[str, dict[str, Any]] = {}
                done, _ = concurrent.futures.wait(futures.keys(), timeout=1.5)
                for fut in done:
                    vp = futures[fut]
                    try:
                        probed_map[vp] = fut.result()
                    except Exception:
                        pass
                # Queue remaining videos silently in background
                for vp, vst in uncached_videos[16:]:
                    rt.media._probe_pool.submit(rt.media.probe_video, vp, vst)
                for f_item in files:
                    if f_item["meta"] is None and f_item["path"] in probed_map:
                        f_item["meta"] = probed_map[f_item["path"]]

            reverse = order != "asc"
            folders.sort(
                key=lambda d: (d["sizeBytes"], d["name"].lower()) if sort_by == "size" else d["name"].lower(),
                reverse=(reverse if sort_by == "size" else False),
            )

            if sort_by == "name":
                files.sort(key=lambda f: f["name"].lower(), reverse=reverse)
            elif sort_by == "date":
                files.sort(key=lambda f: f["modifiedMs"], reverse=reverse)
            elif sort_by == "duration":
                files.sort(
                    key=lambda f: (f["meta"]["durationMs"] if f["meta"] else 0, f["sizeBytes"]),
                    reverse=reverse,
                )
            elif sort_by == "type":
                files.sort(key=lambda f: (f["mediaType"], f["name"].lower()), reverse=reverse)
            else:
                files.sort(key=lambda f: (f["sizeBytes"], f["name"].lower()), reverse=reverse)

            parent_dir = os.path.dirname(safe.rstrip("\\"))
            if len(parent_dir) == 2 and parent_dir[1] == ":":
                parent_dir += "\\"
            if parent_dir == safe or len(safe) <= 3:
                parent_dir = ""

            self._send_json(HTTPStatus.OK, {
                "path": safe,
                "name": os.path.basename(safe.rstrip("\\")) or safe,
                "parentPath": parent_dir,
                "totalSizeBytes": dir_stats["size"],
                "fileCount": dir_stats["files"],
                "mediaCount": dir_stats["media"],
                "diskFreeBytes": disk_free,
                "diskTotalBytes": disk_total,
                "indexer": rt.indexer.status(),
                "folders": folders,
                "files": files,
            })

        def _handle_search(self, qs: dict[str, list[str]]) -> None:
            raw_path = (qs.get("path") or [""])[0]
            q = (qs.get("q") or [""])[0].strip().lower()
            if not q:
                self._send_json(HTTPStatus.OK, {"files": []})
                return
            safe = rt.config.resolve_safe_path(raw_path)
            matches: list[dict[str, Any]] = []
            for root_dir, _dirs, filenames in os.walk(safe):
                if any(skip in root_dir for skip in DiskIndexer.SKIP_NAMES):
                    continue
                for fn in filenames:
                    if q in fn.lower():
                        full = os.path.join(root_dir, fn)
                        try:
                            st = os.stat(full)
                            mtype = classify_media_type(fn)
                            matches.append({
                                "name": fn,
                                "path": full,
                                "sizeBytes": st.st_size,
                                "modifiedMs": int(st.st_mtime * 1000),
                                "mediaType": mtype,
                                "extension": os.path.splitext(fn)[1].lower().lstrip("."),
                                "meta": rt.cache.get(full, st.st_mtime_ns, st.st_size) if mtype == "video" else None,
                            })
                            if len(matches) >= 120:
                                break
                        except OSError:
                            continue
                if len(matches) >= 120:
                    break
            matches.sort(key=lambda x: x["sizeBytes"], reverse=True)
            self._send_json(HTTPStatus.OK, {"files": matches})

        def _handle_largest(self, qs: dict[str, list[str]]) -> None:
            raw_path = (qs.get("path") or [""])[0]
            limit = max(10, min(200, int((qs.get("limit") or ["60"])[0])))
            media_filter = (qs.get("filter") or ["all"])[0]
            safe = rt.config.resolve_safe_path(raw_path)

            heap: list[tuple[int, int, dict[str, Any]]] = []
            seq = 0
            import heapq
            for root_dir, dirs, filenames in os.walk(safe):
                dirs[:] = [d for d in dirs if d not in DiskIndexer.SKIP_NAMES]
                for fn in filenames:
                    mtype = classify_media_type(fn)
                    if media_filter == "video" and mtype != "video":
                        continue
                    if media_filter == "media" and mtype not in ("video", "image"):
                        continue
                    full = os.path.join(root_dir, fn)
                    try:
                        st = os.stat(full, follow_symlinks=False)
                        item = {
                            "name": fn,
                            "path": full,
                            "sizeBytes": st.st_size,
                            "modifiedMs": int(st.st_mtime * 1000),
                            "mediaType": mtype,
                            "extension": os.path.splitext(fn)[1].lower().lstrip("."),
                            "meta": rt.cache.get(full, st.st_mtime_ns, st.st_size) if mtype == "video" else None,
                        }
                        seq += 1
                        if len(heap) < limit:
                            heapq.heappush(heap, (st.st_size, seq, item))
                        elif st.st_size > heap[0][0]:
                            heapq.heapreplace(heap, (st.st_size, seq, item))
                    except OSError:
                        continue

            items = [entry[2] for entry in sorted(heap, key=lambda x: x[0], reverse=True)]
            self._send_json(HTTPStatus.OK, {"path": safe, "files": items})

    return DeckVaultHandler


def start_udp_discovery(rt: CompanionRuntime) -> None:
    """Listen on UDP broadcast port and reply with host metadata for LAN auto-discovery."""
    udp_port = int(rt.config.data.get("udp_port", DEFAULT_UDP_PORT))
    http_port = int(rt.config.data.get("http_port", DEFAULT_HTTP_PORT))

    def loop() -> None:
        sock = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
        sock.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
        sock.setsockopt(socket.SOL_SOCKET, socket.SO_BROADCAST, 1)
        sock.bind(("", udp_port))
        while True:
            try:
                data, addr = sock.recvfrom(1024)
                if data.strip() == DISCOVERY_MAGIC:
                    ips = get_local_ipv4_addresses()
                    # Calculate total free bytes across roots
                    free_sum = 0
                    total_sum = 0
                    for root in rt.config.get_roots():
                        try:
                            u = shutil.disk_usage(root)
                            free_sum += u.free
                            total_sum += u.total
                        except OSError:
                            pass
                    reply = json.dumps({
                        "service": "DeckVault",
                        "version": APP_VERSION,
                        "hostId": rt.config.data["host_id"],
                        "deviceName": rt.config.data["device_name"],
                        "port": http_port,
                        "ips": ips,
                        "freeBytes": free_sum,
                        "totalBytes": total_sum,
                        "ffmpeg": bool(rt.media.ffmpeg),
                    }).encode("utf-8")
                    sock.sendto(reply, addr)
            except OSError:
                time.sleep(0.5)

    threading.Thread(target=loop, daemon=True, name="DeckVaultUDP").start()


def _set_app_user_model_id() -> None:
    if os.name == "nt":
        try:
            ctypes.windll.shell32.SetCurrentProcessExplicitAppUserModelID("DeckVault.App.1")
        except Exception:
            pass


def _resolve_icon_path() -> str | None:
    candidates = [
        Path(getattr(sys, "_MEIPASS", APP_DIR)) / "deckvault.ico",
        APP_DIR / "deckvault.ico",
        Path(r"C:\DeckVault\companion\deckvault.ico"),
        Path(r"C:\DeckVault\app\deckvault.ico"),
    ]
    for p in candidates:
        if p.is_file():
            return str(p)
    return None


def _copy_text_win32(text: str) -> None:
    if os.name != "nt":
        return
    try:
        user32 = ctypes.windll.user32
        kernel32 = ctypes.windll.kernel32
        CF_UNICODETEXT = 13
        GMEM_MOVEABLE = 0x0002
        data = text.encode("utf-16-le") + b"\x00\x00"
        h_mem = kernel32.GlobalAlloc(GMEM_MOVEABLE, len(data))
        if not h_mem:
            return
        ptr = kernel32.GlobalLock(h_mem)
        ctypes.memmove(ptr, data, len(data))
        kernel32.GlobalUnlock(h_mem)
        if user32.OpenClipboard(None):
            user32.EmptyClipboard()
            user32.SetClipboardData(CF_UNICODETEXT, h_mem)
            user32.CloseClipboard()
    except Exception:
        pass


def _find_deckvault_window() -> int:
    """Return HWND of the visible WinForms/WebView DeckVault Desktop window, or 0."""
    if os.name != "nt":
        return 0
    try:
        user32 = ctypes.windll.user32
        found = [0]
        EnumWindowsProc = ctypes.WINFUNCTYPE(ctypes.c_bool, ctypes.c_void_p, ctypes.c_void_p)

        def cb(hwnd: Any, lp: Any) -> bool:
            if not user32.IsWindowVisible(hwnd):
                return True
            tbuf = ctypes.create_unicode_buffer(256)
            user32.GetWindowTextW(hwnd, tbuf, 256)
            if tbuf.value == "DeckVault":
                cbuf = ctypes.create_unicode_buffer(256)
                user32.GetClassNameW(hwnd, cbuf, 256)
                if "WindowsForms" in cbuf.value:
                    found[0] = int(hwnd)
                    return False
            return True

        user32.EnumWindows(EnumWindowsProc(cb), 0)
        return found[0]
    except Exception:
        return 0


def _focus_existing_desktop_window() -> bool:
    """Find an already-open DeckVault window and bring it to foreground cleanly."""
    hwnd = _find_deckvault_window()
    if not hwnd:
        return False
    try:
        user32 = ctypes.windll.user32
        SW_RESTORE = 9
        SWP_NOMOVE_NOSIZE = 0x0002 | 0x0001
        if user32.IsIconic(hwnd):
            user32.ShowWindow(hwnd, SW_RESTORE)
        else:
            user32.ShowWindow(hwnd, 5)
        user32.SetWindowPos(hwnd, -1, 0, 0, 0, 0, SWP_NOMOVE_NOSIZE)
        user32.SetWindowPos(hwnd, -2, 0, 0, 0, 0, SWP_NOMOVE_NOSIZE)
        user32.BringWindowToTop(hwnd)
        user32.SetForegroundWindow(hwnd)
        return True
    except Exception:
        return False


def _acquire_win32_mutex(name: str):
    """Acquire a named Windows mutex; return (handle, already_held)."""
    if os.name != "nt":
        return None, False
    handle = ctypes.windll.kernel32.CreateMutexW(None, False, name)
    ERROR_ALREADY_EXISTS = 183
    already = (ctypes.windll.kernel32.GetLastError() == ERROR_ALREADY_EXISTS)
    return handle, already


def _kill_zombie_window_processes(exclude_pid: int = 0) -> None:
    """Terminate lingering windowless '--window' processes holding the window mutex."""
    if os.name != "nt":
        return
    win_pid_file = CONFIG_DIR / "window.pid"
    killed_fast = False
    if win_pid_file.is_file():
        try:
            old_pid = int(win_pid_file.read_text(encoding="utf-8").strip() or "0")
            if old_pid > 0 and old_pid not in (os.getpid(), int(exclude_pid)):
                h = ctypes.windll.kernel32.OpenProcess(1, False, old_pid)
                if h:
                    ctypes.windll.kernel32.TerminateProcess(h, 0)
                    ctypes.windll.kernel32.CloseHandle(h)
                    killed_fast = True
        except Exception:
            pass
        try:
            win_pid_file.unlink()
        except OSError:
            pass
    if killed_fast:
        return
    try:
        ps_cmd = (
            f"Get-CimInstance Win32_Process -Filter \"Name='DeckVault.exe' OR Name='python.exe' OR Name='pythonw.exe'\" "
            f"| Where-Object {{ $_.CommandLine -like '*--window*' -and $_.ProcessId -ne {int(exclude_pid)} }} "
            f"| ForEach-Object {{ Stop-Process -Id $_.ProcessId -Force -ErrorAction SilentlyContinue }}"
        )
        subprocess.run(
            ["powershell", "-NoProfile", "-WindowStyle", "Hidden", "-Command", ps_cmd],
            creationflags=SUBPROCESS_FLAGS,
            timeout=2,
            stdout=subprocess.DEVNULL,
            stderr=subprocess.DEVNULL,
        )
    except Exception:
        pass


def _make_tray_icon_image():
    """Load DeckVault multi-res icon or draw matte fallback."""
    from PIL import Image, ImageDraw
    ico = _resolve_icon_path()
    if ico:
        try:
            return Image.open(ico).convert("RGBA").resize((64, 64))
        except Exception:
            pass
    size = 64
    img = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    draw = ImageDraw.Draw(img)
    draw.rounded_rectangle((4, 4, size - 4, size - 4), radius=14, fill=(14, 16, 22, 255), outline=(142, 184, 255, 255), width=3)
    draw.polygon([(24, 20), (24, 44), (45, 32)], fill=(142, 184, 255, 255))
    return img


def launch_tray_only(rt: CompanionRuntime, server: ThreadingHTTPServer) -> None:
    """Run lightweight system tray controller without Tkinter or background WebView2 processes."""
    import pystray

    def open_or_focus_app(icon: Any = None, item: Any = None) -> None:
        if not _focus_existing_desktop_window():
            cmd = [sys.executable, "--window"] if getattr(sys, "frozen", False) else [sys.executable, __file__, "--window"]
            subprocess.Popen(cmd, creationflags=SUBPROCESS_FLAGS)

    def copy_pin(icon: Any, item: Any) -> None:
        _copy_text_win32(str(rt.config.data["pin"]))
        rt.log("Pairing PIN copied to clipboard")

    def rescan_disks(icon: Any, item: Any) -> None:
        rt.indexer.start_background_scan()
        rt.log("Started full disk rescan")

    def quit_all(icon: Any, item: Any) -> None:
        try:
            icon.stop()
        except Exception:
            pass
        try:
            server.shutdown()
        except Exception:
            pass

    menu = pystray.Menu(
        pystray.MenuItem("Open DeckVault", open_or_focus_app, default=True),
        pystray.MenuItem(lambda item: f"Copy PIN ({rt.config.data['pin']})", copy_pin),
        pystray.MenuItem("Rescan Disks", rescan_disks),
        pystray.MenuItem("Quit DeckVault", quit_all),
    )
    tray_icon = pystray.Icon(
        "DeckVault",
        _make_tray_icon_image(),
        f"DeckVault ({rt.config.data['device_name']})",
        menu,
    )
    tray_icon.run()


def launch_oled_status_window(
    rt: CompanionRuntime,
    server: ThreadingHTTPServer,
    start_minimized: bool = False,
) -> None:
    """Launch a matte dark window with system-tray minimize support."""
    import tkinter as tk
    from tkinter import messagebox

    root = tk.Tk()
    root.title("DeckVault Companion")
    bg = "#0A0A0D"
    surface = "#14151A"
    border = "#282A34"
    accent = "#AAB4CE"
    fg = "#EDEDEF"
    muted = "#9496A3"
    btn_bg = "#1D1E26"
    btn_active = "#292B36"

    root.configure(bg=bg)
    root.geometry("500x540")
    root.resizable(False, False)

    tk.Label(
        root, text="DeckVault Companion", font=("Segoe UI Semibold", 17),
        bg=bg, fg=fg,
    ).pack(pady=(16, 2))
    tk.Label(
        root,
        text=f"{rt.config.data['device_name']}  •  Port {rt.config.data['http_port']}",
        font=("Segoe UI", 10), bg=bg, fg=muted,
    ).pack(pady=(0, 12))

    pin_card = tk.Frame(root, bg=surface, highlightbackground=border, highlightthickness=1)
    pin_card.pack(fill="x", padx=22, pady=6)
    tk.Label(
        pin_card, text="PAIRING PIN (ENTER ON GALAXY TAB / Z FOLD)",
        font=("Segoe UI Semibold", 9), bg=surface, fg=muted,
    ).pack(pady=(12, 2))
    pin_var = tk.StringVar(value=str(rt.config.data["pin"]))
    tk.Label(
        pin_card, textvariable=pin_var, font=("Consolas", 30, "bold"),
        bg=surface, fg=accent,
    ).pack(pady=(0, 8))

    ips_text = "   |   ".join(get_local_ipv4_addresses())
    tk.Label(
        pin_card, text=f"LAN IP: {ips_text}", font=("Consolas", 10),
        bg=surface, fg=fg,
    ).pack(pady=(0, 10))

    btn_row = tk.Frame(root, bg=bg)
    btn_row.pack(fill="x", padx=22, pady=8)

    tray_icon_holder: list[Any] = [None]
    is_quitting = [False]

    def on_new_pin() -> None:
        pin_var.set(rt.config.rotate_pin())
        rt.log("Generated new pairing PIN")

    def on_rescan() -> None:
        rt.indexer.start_background_scan()
        rt.log("Started full disk usage rescan")

    def on_firewall() -> None:
        ps_script = APP_DIR / "install_firewall_rule.ps1"
        if ps_script.exists():
            subprocess.Popen([
                "powershell", "-ExecutionPolicy", "Bypass", "-Command",
                f"Start-Process powershell -Verb RunAs -ArgumentList '-ExecutionPolicy Bypass -File \"{ps_script}\"'"
            ], creationflags=SUBPROCESS_FLAGS)
        else:
            messagebox.showinfo("Firewall", f"Allow TCP {rt.config.data['http_port']} and UDP {rt.config.data['udp_port']}")

    def hide_to_tray() -> None:
        if tray_icon_holder[0] is not None:
            root.withdraw()
        else:
            root.iconify()

    def show_from_tray() -> None:
        root.deiconify()
        try:
            root.state("normal")
        except Exception:
            pass
        root.lift()
        root.focus_force()

    def quit_app() -> None:
        is_quitting[0] = True
        try:
            if tray_icon_holder[0] is not None:
                tray_icon_holder[0].stop()
        except Exception:
            pass
        try:
            server.shutdown()
        except Exception:
            pass
        try:
            root.destroy()
        except Exception:
            pass

    def on_open_desktop_app() -> None:
        cmd = [sys.executable, "--window"] if getattr(sys, "frozen", False) else [sys.executable, __file__, "--window"]
        subprocess.Popen(cmd, creationflags=SUBPROCESS_FLAGS)

    for label, cmd in [
        ("Open App", on_open_desktop_app),
        ("New PIN", on_new_pin),
        ("Rescan", on_rescan),
        ("Firewall", on_firewall),
        ("Hide to Tray", hide_to_tray),
    ]:
        tk.Button(
            btn_row, text=label, command=cmd, bg=btn_bg, fg=fg,
            activebackground=btn_active, activeforeground=fg,
            relief="flat", font=("Segoe UI Semibold", 9), padx=10, pady=7,
        ).pack(side="left", expand=True, fill="x", padx=3)

    status_var = tk.StringVar(value="Indexing drives...")
    tk.Label(root, textvariable=status_var, font=("Segoe UI", 9), bg=bg, fg="#8FB8A0").pack(pady=(6, 4))

    log_box = tk.Text(
        root, height=10, bg="#0D0E12", fg="#C5C7D2", insertbackground=fg,
        relief="flat", font=("Consolas", 9), padx=10, pady=8, state="disabled",
        highlightbackground=border, highlightthickness=1,
    )
    log_box.pack(fill="both", expand=True, padx=22, pady=(4, 8))

    bottom_row = tk.Frame(root, bg=bg)
    bottom_row.pack(fill="x", padx=22, pady=(0, 14))
    tk.Label(
        bottom_row, text="Minimize or X hides to Windows tray (hidden tasks)",
        font=("Segoe UI", 8), bg=bg, fg=muted,
    ).pack(side="left")
    tk.Button(
        bottom_row, text="Quit Server", command=quit_app,
        bg="#261B1D", fg="#CF8C92", activebackground="#382428", activeforeground="#CF8C92",
        relief="flat", font=("Segoe UI Semibold", 8), padx=10, pady=4,
    ).pack(side="right")

    # Setup Windows system tray icon (hidden tasks) via pystray
    try:
        import pystray

        def tray_copy_pin(icon: Any, item: Any) -> None:
            def _do() -> None:
                root.clipboard_clear()
                root.clipboard_append(str(rt.config.data["pin"]))
                rt.log("Pairing PIN copied to clipboard")
            root.after(0, _do)

        menu = pystray.Menu(
            pystray.MenuItem("Open DeckVault App", lambda icon, item: root.after(0, on_open_desktop_app), default=True),
            pystray.MenuItem("Show Server Status", lambda icon, item: root.after(0, show_from_tray)),
            pystray.MenuItem(lambda item: f"Copy PIN ({rt.config.data['pin']})", tray_copy_pin),
            pystray.MenuItem("Rescan Disks", lambda icon, item: root.after(0, on_rescan)),
            pystray.MenuItem("Quit Companion", lambda icon, item: root.after(0, quit_app)),
        )
        tray_icon = pystray.Icon(
            "DeckVaultCompanion",
            _make_tray_icon_image(),
            f"DeckVault ({rt.config.data['device_name']})",
            menu,
        )
        tray_icon_holder[0] = tray_icon
        threading.Thread(target=tray_icon.run, daemon=True, name="DeckVaultTray").start()
    except Exception as exc:
        rt.log(f"Tray icon unavailable ({exc}); minimize stays on taskbar")

    def on_unmap(event: Any) -> None:
        if not is_quitting[0] and tray_icon_holder[0] is not None and event.widget is root:
            try:
                if root.state() == "iconic":
                    root.after(10, root.withdraw)
            except Exception:
                pass

    root.bind("<Unmap>", on_unmap)

    def poll() -> None:
        if is_quitting[0]:
            return
        st = rt.indexer.status()
        saved_mb = rt.config.data.get("space_saved_bytes", 0) / (1024 * 1024)
        ff_txt = "FFmpeg ready" if rt.media.ffmpeg else "FFmpeg MISSING"
        scan_txt = "Scanning disks..." if st["scanning"] else f"{st['indexedDirs']} folders indexed"
        status_var.set(f"{scan_txt}  •  {ff_txt}  •  Saved: {saved_mb:.1f} MB")

        lines = []
        while not rt.events.empty():
            lines.append(rt.events.get_nowait())
        if lines:
            log_box.configure(state="normal")
            for ln in lines:
                log_box.insert("end", ln + "\n")
            log_box.see("end")
            log_box.configure(state="disabled")
        root.after(500, poll)

    def on_close() -> None:
        if tray_icon_holder[0] is not None:
            hide_to_tray()
        else:
            quit_app()

    root.protocol("WM_DELETE_WINDOW", on_close)
    if start_minimized and tray_icon_holder[0] is not None:
        root.withdraw()
    root.after(200, poll)
    root.mainloop()


def _apply_window_icon_to_native() -> None:
    if os.name != "nt":
        return
    ico = _resolve_icon_path()
    if not ico:
        return
    try:
        user32 = ctypes.windll.user32
        hwnd = _find_deckvault_window() or user32.FindWindowW(None, "DeckVault")
        if not hwnd:
            return
        IMAGE_ICON = 1
        LR_LOADFROMFILE = 0x0010
        WM_SETICON = 0x0080
        h_small = user32.LoadImageW(None, ico, IMAGE_ICON, 16, 16, LR_LOADFROMFILE)
        h_big = user32.LoadImageW(None, ico, IMAGE_ICON, 48, 48, LR_LOADFROMFILE)
        if h_small:
            user32.SendMessageW(hwnd, WM_SETICON, 0, h_small)
        if h_big:
            user32.SendMessageW(hwnd, WM_SETICON, 1, h_big)
    except Exception:
        pass


def _launch_desktop_webview(http_port: int, force_process_exit: bool = True) -> None:
    if _focus_existing_desktop_window():
        return
    _set_app_user_model_id()
    mutex_name = "Local\\DeckVaultDesktopWindowMutex_v1"
    mutex_h, already_open = _acquire_win32_mutex(mutex_name)
    if already_open:
        for _ in range(6):
            if _find_deckvault_window():
                _focus_existing_desktop_window()
                if mutex_h and os.name == "nt":
                    try:
                        ctypes.windll.kernel32.CloseHandle(mutex_h)
                    except Exception:
                        pass
                return
            time.sleep(0.25)
        # Mutex holder has no visible window (stale/zombie process); reclaim cleanly.
        if mutex_h and os.name == "nt":
            try:
                ctypes.windll.kernel32.CloseHandle(mutex_h)
            except Exception:
                pass
        _kill_zombie_window_processes(os.getpid())
        time.sleep(0.15)
        mutex_h, already_open = _acquire_win32_mutex(mutex_name)
        if already_open and _find_deckvault_window():
            _focus_existing_desktop_window()
            return

    win_pid_file = CONFIG_DIR / "window.pid"
    try:
        win_pid_file.write_text(str(os.getpid()), encoding="utf-8")
    except Exception:
        pass

    import webview
    storage_dir = str(CONFIG_DIR / "webview_cache")
    os.makedirs(storage_dir, exist_ok=True)
    state: dict[str, Any] = {"win": None, "is_max": False, "closed": False}
    mutex_holder = [mutex_h]

    def _release_mutex() -> None:
        h = mutex_holder[0]
        if h and os.name == "nt":
            mutex_holder[0] = None
            try:
                ctypes.windll.kernel32.CloseHandle(h)
            except Exception:
                pass
        try:
            if win_pid_file.is_file() and win_pid_file.read_text(encoding="utf-8").strip() == str(os.getpid()):
                win_pid_file.unlink()
        except Exception:
            pass

    def _schedule_force_exit(delay_s: float = 0.45) -> None:
        _release_mutex()
        if force_process_exit:
            t = threading.Timer(delay_s, lambda: os._exit(0))
            t.daemon = True
            t.start()

    def _on_window_closed() -> None:
        state["closed"] = True
        _schedule_force_exit(0.45)

    class DesktopWindowApi:
        """Pure-method JS bridge; avoids exposing Window/.NET objects to pywebview serializer."""

        def minimize(self) -> None:
            win = state["win"]
            if win is not None:
                win.minimize()

        def toggle_max(self) -> bool:
            win = state["win"]
            if win is None:
                return False
            if state["is_max"]:
                win.restore()
                state["is_max"] = False
            else:
                win.maximize()
                state["is_max"] = True
            return state["is_max"]

        def toggle_fullscreen(self) -> None:
            win = state["win"]
            if win is not None:
                win.toggle_fullscreen()

        def close(self) -> None:
            win = state["win"]
            if win is not None:
                try:
                    win.destroy()
                except Exception:
                    pass
            _schedule_force_exit(0.55)

        def get_bounds(self) -> dict[str, int]:
            win = state["win"]
            if win is None:
                return {"x": 0, "y": 0, "w": 1340, "h": 860}
            return {"x": int(win.x), "y": int(win.y), "w": int(win.width), "h": int(win.height)}

        def set_bounds(self, x: int, y: int, w: int, h: int) -> None:
            win = state["win"]
            if win is None:
                return
            nw = max(760, int(w))
            nh = max(520, int(h))
            win.move(int(x), int(y))
            win.resize(nw, nh)

        def start_native_drag(self, local_paths: list[str]) -> bool:
            """Initiate a Windows OLE FileDrop drag out of DeckVault into external apps (Dropbox/Explorer)."""
            if os.name != "nt" or not local_paths:
                return False
            valid = [os.path.abspath(str(p)) for p in local_paths if os.path.exists(str(p))]
            if not valid:
                return False
            try:
                import clr
                clr.AddReference("System.Windows.Forms")
                import System
                from System.Windows.Forms import Application, DataObject, DataFormats, DragDropEffects
                if Application.OpenForms.Count <= 0:
                    return False
                form = Application.OpenForms[0]

                def invoke_drag() -> None:
                    arr = System.Array[str](valid)
                    data_obj = DataObject(DataFormats.FileDrop, arr)
                    form.DoDragDrop(data_obj, DragDropEffects.Copy | DragDropEffects.Move)

                form.BeginInvoke(System.Action(invoke_drag))
                return True
            except Exception:
                return False

    api = DesktopWindowApi()
    win = webview.create_window(
        "DeckVault",
        f"http://127.0.0.1:{http_port}/desktop",
        js_api=api,
        width=1360,
        height=880,
        min_size=(760, 520),
        frameless=True,
        easy_drag=False,
        shadow=True,
        background_color="#000000",
        text_select=True,
    )
    state["win"] = win
    win.events.shown += _apply_window_icon_to_native
    win.events.closed += _on_window_closed
    ico = _resolve_icon_path()
    try:
        webview.start(gui="edgechromium", private_mode=False, storage_path=storage_dir, icon=ico)
    finally:
        _release_mutex()
        if force_process_exit:
            os._exit(0)


def _is_local_server_up(http_port: int) -> bool:
    try:
        with urllib.request.urlopen(f"http://127.0.0.1:{http_port}/api/ping", timeout=0.6) as resp:
            return resp.status == 200
    except Exception:
        return False


def main() -> None:
    _set_app_user_model_id()
    parser = argparse.ArgumentParser(description="DeckVault Unified App & Server")
    parser.add_argument("--headless", action="store_true", help="Run server without UI/tray")
    parser.add_argument("--tray", "--minimized", dest="tray", action="store_true", help="Run lightweight server in system tray")
    parser.add_argument("--window", "--desktop", dest="window", action="store_true", help="Open single-instance Desktop window")
    parser.add_argument("--app", action="store_true", help="Ensure tray server is up and open or focus DeckVault window")
    parser.add_argument("--port", type=int, default=0, help="Override HTTP port")
    args = parser.parse_args()

    rt = CompanionRuntime()
    if args.port > 0:
        rt.config.data["http_port"] = args.port
        rt.config.save()

    http_port = int(rt.config.data["http_port"])

    # Default app launch (--app): single-instance focus or start tray server + open window cleanly
    if args.app:
        if os.name == "nt":
            try:
                ctypes.windll.user32.AllowSetForegroundWindow(-1)
            except Exception:
                pass
        if _focus_existing_desktop_window():
            return
        if not _is_local_server_up(http_port):
            tray_cmd = [sys.executable, "--tray"] if getattr(sys, "frozen", False) else [sys.executable, __file__, "--tray"]
            subprocess.Popen(tray_cmd, creationflags=SUBPROCESS_FLAGS)
            for _ in range(30):
                if _is_local_server_up(http_port):
                    break
                time.sleep(0.15)
        try:
            freq = urllib.request.Request(f"http://127.0.0.1:{http_port}/api/desktop/focus", data=b"{}", headers={"Content-Type": "application/json"}, method="POST")
            urllib.request.urlopen(freq, timeout=3).close()
            return
        except Exception:
            pass
        args.window = True

    if args.window:
        if _focus_existing_desktop_window():
            return
        if _is_local_server_up(http_port):
            _launch_desktop_webview(http_port)
            return

    srv_mutex, srv_already = _acquire_win32_mutex("Local\\DeckVaultServerMutex_v1")
    if srv_already and not args.window:
        _focus_existing_desktop_window()
        return

    try:
        server = ThreadingHTTPServer(("0.0.0.0", http_port), make_handler(rt))
    except OSError:
        if args.window:
            _focus_existing_desktop_window()
        sys.exit(0)

    server.daemon_threads = True

    start_udp_discovery(rt)
    rt.indexer.start_background_scan()

    ips = ", ".join(get_local_ipv4_addresses())
    rt.log(f"DeckVault v{APP_VERSION} on '{rt.config.data['device_name']}'")
    rt.log(f"HTTP :{http_port}  |  UDP Discovery :{rt.config.data['udp_port']}")
    rt.log(f"LAN IPs: {ips}")
    rt.log(f"Pairing PIN: {rt.config.data['pin']}")
    rt.log(f"FFmpeg: {rt.media.ffmpeg or 'NOT FOUND'}")

    if args.window:
        srv_thread = threading.Thread(target=server.serve_forever, daemon=True)
        srv_thread.start()
        try:
            _launch_desktop_webview(http_port, force_process_exit=False)
        finally:
            server.shutdown()
            os._exit(0)
        return

    if args.tray:
        srv_thread = threading.Thread(target=server.serve_forever, daemon=True)
        srv_thread.start()
        try:
            launch_tray_only(rt, server)
        except Exception:
            try:
                while True:
                    time.sleep(1)
            except KeyboardInterrupt:
                server.shutdown()
        return

    if args.headless:
        try:
            server.serve_forever()
        except KeyboardInterrupt:
            server.shutdown()
    else:
        srv_thread = threading.Thread(target=server.serve_forever, daemon=True)
        srv_thread.start()
        try:
            launch_oled_status_window(rt, server, start_minimized=False)
        except Exception:
            try:
                while True:
                    time.sleep(1)
            except KeyboardInterrupt:
                server.shutdown()


if __name__ == "__main__":
    main()
