@echo off
title Cap Nhat IP Server Nhanh
color 0E
cls
echo.
echo ========================================
echo   CAP NHAT IP SERVER NHANH
echo   RTP AV Conference
echo ========================================
echo.
echo Script nay se cap nhat IP: 
echo vao tat ca START-CLIENT-*.bat
echo.
pause

cd /d "%~dp0"

powershell -NoProfile -ExecutionPolicy Bypass -File "CAP-NHAT-IP-NHANH.ps1"

if errorlevel 1 (
    echo.
    echo [ERROR] Co loi khi cap nhat!
    echo.
    pause
    exit /b 1
)

exit /b 0

