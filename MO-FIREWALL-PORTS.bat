@echo off
title Mo Firewall Ports cho RTP AV Conference
color 0E
cls
echo.
echo ========================================
echo   MO FIREWALL PORTS
echo   RTP AV Conference Server
echo ========================================
echo.
echo [INFO] Script nay se mo cac port sau:
echo   - Port 1099 (TCP) - RMI Registry
echo   - Port 2099 (TCP) - RMI Service
echo   - Port 5004 (UDP) - RTP Audio/Video
echo.
echo [CHU Y] Can chay voi quyen Administrator!
echo [CHU Y] Cac rules nay se LUON MO (vi vien)
echo.
pause

REM Kiem tra quyen Administrator
net session >nul 2>&1
if errorlevel 1 (
    echo.
    echo [ERROR] Can chay voi quyen Administrator!
    echo [ERROR] Click chuot phai vao file va chon "Run as administrator"
    echo.
    pause
    exit /b 1
)

echo.
echo [INFO] Dang mo firewall ports...
echo.

REM Mo Port 1099 (TCP) - RMI Registry
echo [1/3] Dang mo Port 1099 (TCP)...
netsh advfirewall firewall delete rule name="RTP AV Conference - RMI Registry (1099)" >nul 2>&1
netsh advfirewall firewall add rule name="RTP AV Conference - RMI Registry (1099)" dir=in action=allow protocol=TCP localport=1099 >nul 2>&1
if errorlevel 1 (
    echo [ERROR] Khong the mo Port 1099!
) else (
    echo [SUCCESS] Da mo Port 1099 (TCP)
)

REM Mo Port 2099 (TCP) - RMI Service
echo [2/3] Dang mo Port 2099 (TCP)...
netsh advfirewall firewall delete rule name="RTP AV Conference - RMI Service (2099)" >nul 2>&1
netsh advfirewall firewall add rule name="RTP AV Conference - RMI Service (2099)" dir=in action=allow protocol=TCP localport=2099 >nul 2>&1
if errorlevel 1 (
    echo [ERROR] Khong the mo Port 2099!
) else (
    echo [SUCCESS] Da mo Port 2099 (TCP)
)

REM Mo Port 5004 (UDP) - RTP Audio/Video
echo [3/3] Dang mo Port 5004 (UDP)...
netsh advfirewall firewall delete rule name="RTP AV Conference - RTP Audio/Video (5004)" >nul 2>&1
netsh advfirewall firewall add rule name="RTP AV Conference - RTP Audio/Video (5004)" dir=in action=allow protocol=UDP localport=5004 >nul 2>&1
if errorlevel 1 (
    echo [ERROR] Khong the mo Port 5004!
) else (
    echo [SUCCESS] Da mo Port 5004 (UDP)
)

echo.
echo ========================================
echo   [HOAN TAT] Da mo tat ca firewall ports!
echo ========================================
echo.
echo [INFO] Cac ports da duoc mo:
echo   - Port 1099 (TCP) - RMI Registry
echo   - Port 2099 (TCP) - RMI Service
echo   - Port 5004 (UDP) - RTP Audio/Video
echo.
echo [CHU Y] Cac rules nay la VI VIEN!
echo [CHU Y] Sau khi tat may va bat lai, cac ports van se MO!
echo.
echo [INFO] De kiem tra, chay lenh:
echo   netsh advfirewall firewall show rule name=all | findstr "RTP AV"
echo.
pause

