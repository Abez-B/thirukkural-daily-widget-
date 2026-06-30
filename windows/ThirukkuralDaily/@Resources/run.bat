@echo off
:: Robust Python launcher for Thirukkural Daily Rainmeter skin
:: Tries python, then py (Windows launcher), then python3
set ARG=%1
set SCRIPT=%~dp0fetch_kural.py

python "%SCRIPT%" %ARG% 2>nul && exit /b 0
py "%SCRIPT%" %ARG% 2>nul && exit /b 0
python3 "%SCRIPT%" %ARG% 2>nul && exit /b 0

:: If all fail, log an error
echo Python not found. Install Python from https://python.org > "%~dp0error.log"
exit /b 1
