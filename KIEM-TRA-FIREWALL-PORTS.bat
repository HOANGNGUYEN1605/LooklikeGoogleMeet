@echo off
title Kiem Tra Firewall Ports
color 0B
cls
echo.
echo ========================================
echo   KIEM TRA FIREWALL PORTS
echo   RTP AV Conference Server
echo ========================================
echo.
echo [INFO] Dang kiem tra cac firewall rules...
echo.

netsh advfirewall firewall show rule name=all | findstr /C:"RTP AV Conference" /C:"1099" /C:"2099" /C:"5004"

if errorlevel 1 (
    echo.
    echo [WARNING] Khong tim thay firewall rules!
    echo [WARNING] Chay MO-FIREWALL-PORTS.bat de mo cac ports.
) else (
    echo.
    echo [SUCCESS] Da tim thay firewall rules!
    echo [SUCCESS] Cac ports da duoc mo.
)

echo.
pause

