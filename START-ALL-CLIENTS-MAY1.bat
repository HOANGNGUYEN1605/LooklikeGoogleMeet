@echo off
title RTP AV Conference - MAY 1: SERVER + 3 CLIENTS
color 0A
cls
echo.
echo ========================================
echo   RTP AV Conference - MAY 1
echo   SERVER + 3 CLIENTS (Alice, Bob, Charlie)
echo ========================================
echo.
echo [CHU Y] Script nay se mo:
echo   - 1 cua so SERVER
echo   - 3 cua so CLIENT (Alice, Bob, Charlie)
echo.
echo [CHU Y] Tat ca se chay trong cung 1 room: demo
echo.
pause

cd /d "%~dp0"

REM Khoi dong SERVER trong cua so moi
start "RTP AV Conference - SERVER" cmd /k START-SERVER.bat

REM Doi 5 giay de server khoi dong
echo.
echo [INFO] Dang cho server khoi dong (5 giay)...
timeout /t 5 /nobreak >nul

REM Khoi dong 3 CLIENT trong cac cua so moi
echo [INFO] Dang khoi dong 3 clients...
start "RTP AV Conference - CLIENT 1 (Alice)" cmd /k START-CLIENT-1.bat
timeout /t 2 /nobreak >nul
start "RTP AV Conference - CLIENT 2 (Bob)" cmd /k START-CLIENT-2.bat
timeout /t 2 /nobreak >nul
start "RTP AV Conference - CLIENT 3 (Charlie)" cmd /k START-CLIENT-3.bat

echo.
echo ========================================
echo   [SUCCESS] Da khoi dong:
echo   - 1 SERVER
echo   - 3 CLIENTS (Alice, Bob, Charlie)
echo ========================================
echo.
echo [CHU Y] Tat ca cua so phai MO de chay!
echo [CHU Y] Dang nhap vao room: demo
echo.
pause

