@echo off
setlocal
cd /d "%~dp0"
python "%~dp0deckvault_server.py" --headless
pause
