@echo off
title RTP AV Conference - MAY 2: 3 CLIENTS
color 0B
cls
echo.
echo ========================================
echo   RTP AV Conference - MAY 2
echo   3 CLIENTS (David, Eve, Frank)
echo ========================================
echo.
echo [CHU Y] Script nay se mo 3 cua so CLIENT
echo   - David (Port 6003)
echo   - Eve (Port 6004)
echo   - Frank (Port 6005)
echo.
echo [CHU Y] Tat ca se join vao room: demo
echo [CHU Y] Can SERVER da chay tren may khac!
echo.
pause

cd /d "%~dp0"

REM Kiem tra SERVER_IP trong START-CLIENT-4.bat
findstr /C:"set SERVER_IP=" START-CLIENT-4.bat >nul
if errorlevel 1 (
    echo [ERROR] Khong tim thay SERVER_IP trong START-CLIENT-4.bat
    echo [ERROR] Vui long cap nhat IP LAN cua SERVER!
    pause
    exit /b 1
)

echo [INFO] Dang khoi dong 3 clients...
start "RTP AV Conference - CLIENT 4 (David)" cmd /k START-CLIENT-4.bat
timeout /t 2 /nobreak >nul
start "RTP AV Conference - CLIENT 5 (Eve)" cmd /k START-CLIENT-5.bat
timeout /t 2 /nobreak >nul
start "RTP AV Conference - CLIENT 6 (Frank)" cmd /k START-CLIENT-6.bat

echo.
echo ========================================
echo   [SUCCESS] Da khoi dong 3 CLIENTS:
echo   - David (Port 6003)
echo   - Eve (Port 6004)
echo   - Frank (Port 6005)
echo ========================================
echo.
echo [CHU Y] Tat ca cua so phai MO de chay!
echo [CHU Y] Dang nhap vao room: demo
echo [CHU Y] Can SERVER da chay tren may khac!
echo.
pause

