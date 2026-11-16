@echo off
setlocal
cd /d "%~dp0"

if exist "C:\DeckVault\app\DeckVault.exe" (
    start "" "C:\DeckVault\app\DeckVault.exe" --tray %*
    exit /b 0
)
if exist "%~dp0DeckVaultCompanion.exe" (
    start "" "%~dp0DeckVaultCompanion.exe" %*
    exit /b 0
)

where python >nul 2>&1
if errorlevel 1 (
    echo [DeckVault] Python 3.10+ is required. Install from Microsoft Store or python.org.
    pause
    exit /b 1
)

where ffmpeg >nul 2>&1
if errorlevel 1 (
    if not exist "C:\ffmpeg\bin\ffmpeg.exe" (
        echo [DeckVault] FFmpeg not found. Attempting install via winget...
        winget install -e --id Gyan.FFmpeg --accept-source-agreements --accept-package-agreements
    )
)

echo [DeckVault] Starting PC/Laptop Companion Server...
start "" pythonw "%~dp0deckvault_server.py"
if errorlevel 1 (
    python "%~dp0deckvault_server.py"
)
