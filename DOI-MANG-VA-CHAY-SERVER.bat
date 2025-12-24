@echo off
setlocal enabledelayedexpansion
title Doi Mang va Chay Server - RTP AV Conference
color 0A
cls
echo.
echo ========================================
echo   DOI MANG VA CHAY SERVER
echo   RTP AV Conference
echo ========================================
echo.
echo Script nay se:
echo   1. Cap nhat IP LAN moi (neu doi mang)
echo   2. Kiem tra Docker
echo   3. Khoi dong MySQL container
echo   4. Khoi dong server
echo.
echo [CHU Y] Chay script nay sau khi doi mang (WiFi/Router)
echo.
pause

cd /d "%~dp0"

REM ========================================
REM BUOC 1: CAP NHAT IP LAN
REM ========================================
echo.
echo ========================================
echo   BUOC 1: CAP NHAT IP LAN
echo ========================================
echo.

REM Tim IP LAN hien tai
echo [INFO] Dang tim IP LAN hien tai...
powershell -Command ^
"$adapters = Get-NetAdapter | Where-Object {$_.Status -eq 'Up' -and $_.InterfaceDescription -notlike '*Virtual*' -and $_.InterfaceDescription -notlike '*ZeroTier*' -and $_.InterfaceDescription -notlike '*Hyper-V*' -and $_.InterfaceDescription -notlike '*WSL*'}; ^
$ip = $null; ^
foreach ($adapter in $adapters) { ^
    $config = Get-NetIPAddress -InterfaceIndex $adapter.ifIndex -AddressFamily IPv4 -ErrorAction SilentlyContinue; ^
    if ($config) { ^
        $ipAddr = $config.IPAddress; ^
        if ($ipAddr -match '^192\.168\.' -and $ipAddr -notmatch '^192\.168\.56\.') { ^
            $ip = $ipAddr; ^
            break; ^
        } ^
        if ($ipAddr -match '^10\.') { ^
            $ip = $ipAddr; ^
            break; ^
        } ^
        if ($ipAddr -match '^172\.(1[6-9]|2[0-9]|3[0-1])\.' -and $ipAddr -notmatch '^172\.24\.' -and $ipAddr -notmatch '^172\.30\.') { ^
            $ip = $ipAddr; ^
            break; ^
        } ^
    } ^
}; ^
if ($ip) { Write-Host $ip } else { exit 1 }" > temp_ip.txt

if errorlevel 1 (
    echo [WARNING] Khong tim thay IP LAN!
    echo [WARNING] Bo qua cap nhat IP. Tiep tuc voi IP cu...
    del temp_ip.txt 2>nul
    goto :skip_ip_update
)

set /p SERVER_IP=<temp_ip.txt
del temp_ip.txt 2>nul

if not defined SERVER_IP (
    echo [WARNING] Khong tim thay IP LAN!
    echo [WARNING] Bo qua cap nhat IP. Tiep tuc voi IP cu...
    goto :skip_ip_update
)

echo [SUCCESS] Tim thay IP LAN: %SERVER_IP%

REM Kiem tra IP cu
set "OLD_IP="
for %%f in (START-CLIENT-*.bat) do (
    for /f "tokens=2 delims==" %%a in ('findstr /C:"set SERVER_IP=" "%%f"') do (
        set "OLD_IP=%%a"
        goto :found_old
    )
)
:found_old

if defined OLD_IP (
    if "%OLD_IP%"=="%SERVER_IP%" (
        echo [INFO] IP khong thay doi. Khong can cap nhat.
        goto :skip_ip_update
    )
    echo [INFO] IP cu: %OLD_IP%
    echo [INFO] IP moi: %SERVER_IP%
)

REM Cap nhat IP
echo [INFO] Dang cap nhat IP vao cac client scripts...
set "UPDATED_COUNT=0"
for %%f in (START-CLIENT-*.bat) do (
    powershell -Command "(Get-Content '%%f') -replace 'set SERVER_IP=.*', 'set SERVER_IP=%SERVER_IP%' | Set-Content '%%f'" >nul 2>&1
    if !errorlevel! equ 0 (
        set /a UPDATED_COUNT+=1
    )
)
echo [SUCCESS] Da cap nhat %UPDATED_COUNT% file(s)!

:skip_ip_update
echo.

REM ========================================
REM BUOC 2: KIEM TRA DOCKER
REM ========================================
echo ========================================
echo   BUOC 2: KIEM TRA DOCKER
echo ========================================
echo.

docker ps >nul 2>&1
if %errorlevel% neq 0 (
    echo [ERROR] Docker khong chay!
    echo [ERROR] Vui long mo Docker Desktop va cho no khoi dong xong!
    echo.
    pause
    exit /b 1
)
echo [SUCCESS] Docker dang chay!
echo.

REM ========================================
REM BUOC 3: KHOI DONG MYSQL CONTAINER
REM ========================================
echo ========================================
echo   BUOC 3: KHOI DONG MYSQL CONTAINER
echo ========================================
echo.

docker ps | findstr "rtp-mysql" >nul
if %errorlevel% neq 0 (
    echo [INFO] MySQL container khong chay. Dang khoi dong...
    docker start rtp-mysql >nul 2>&1
    if %errorlevel% neq 0 (
        echo [ERROR] Khong the khoi dong MySQL container!
        echo [ERROR] Chay: database\setup-docker-mysql.bat
        echo.
        pause
        exit /b 1
    )
    echo [INFO] Doi MySQL khoi dong (10 giay)...
    timeout /t 10 /nobreak >nul
    
    :wait_mysql
    docker exec rtp-mysql mysqladmin ping -h localhost --silent >nul 2>&1
    if %errorlevel% neq 0 (
        echo [INFO] Dang doi MySQL...
        timeout /t 3 /nobreak >nul
        goto :wait_mysql
    )
    echo [SUCCESS] MySQL da san sang!
) else (
    echo [SUCCESS] MySQL container dang chay!
)
echo.

REM ========================================
REM BUOC 4: KHOI DONG SERVER
REM ========================================
echo ========================================
echo   BUOC 4: KHOI DONG SERVER
echo ========================================
echo.

echo [INFO] Dang khoi dong server...
start "RTP AV Conference - SERVER" cmd /k "START-SERVER.bat"

echo.
echo ========================================
echo   [HOAN TAT] Server dang khoi dong!
echo ========================================
echo.
echo [THONG TIN]
if defined SERVER_IP (
    echo   IP LAN moi: %SERVER_IP%
)
if defined OLD_IP (
    if not "%OLD_IP%"=="%SERVER_IP%" (
        echo   IP LAN cu: %OLD_IP%
    )
)
echo   MySQL container: DANG CHAY
echo   Server: DANG KHOI DONG
echo   Firewall ports: DA MO (vi vien)
echo.
echo [BƯỚC TIẾP THEO]
echo   Doi den khi thay "RMI ready" trong cua so server!
echo   Sau do chay client: START-CLIENT-*.bat
echo.
pause

