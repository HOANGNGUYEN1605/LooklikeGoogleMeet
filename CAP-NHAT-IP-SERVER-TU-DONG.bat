@echo off
setlocal enabledelayedexpansion
title Cap Nhat IP Server Tu Dong
color 0E
cls
echo.
echo ========================================
echo   CAP NHAT IP SERVER TU DONG
echo   RTP AV Conference
echo ========================================
echo.
echo Script nay se:
echo   1. Tim IP LAN hien tai cua may
echo   2. Cap nhat IP vao tat ca START-CLIENT-*.bat
echo   3. Hien thi ket qua
echo.
echo [CHU Y] Chay script nay moi khi doi mang (WiFi/Router)
echo.
pause

cd /d "%~dp0"

REM Tim IP LAN hien tai
echo.
echo [Step 1] Dang tim IP LAN hien tai...
echo.

REM Lay IP LAN (bo qua ZeroTier, VirtualBox, etc.)
for /f "tokens=2 delims=:" %%a in ('ipconfig ^| findstr /C:"IPv4"') do (
    set "IP_LINE=%%a"
    set "IP_LINE=!IP_LINE: =!"
    
    REM Bo qua cac IP khong phai LAN
    echo !IP_LINE! | findstr /R "^192\.168\." >nul
    if !errorlevel! equ 0 (
        set "SERVER_IP=!IP_LINE!"
        goto :found_ip
    )
    
    echo !IP_LINE! | findstr /R "^10\." >nul
    if !errorlevel! equ 0 (
        set "SERVER_IP=!IP_LINE!"
        goto :found_ip
    )
    
    echo !IP_LINE! | findstr /R "^172\.2[0-9]\." >nul
    if !errorlevel! equ 0 (
        REM Kiem tra khong phai ZeroTier (172.24.x.x) hoac VirtualBox (172.30.x.x)
        echo !IP_LINE! | findstr /R "^172\.24\." >nul
        if !errorlevel! neq 0 (
            echo !IP_LINE! | findstr /R "^172\.30\." >nul
            if !errorlevel! neq 0 (
                set "SERVER_IP=!IP_LINE!"
                goto :found_ip
            )
        )
    )
)

:found_ip
if not defined SERVER_IP (
    echo [ERROR] Khong tim thay IP LAN!
    echo [ERROR] Vui long kiem tra ket noi mang.
    echo.
    pause
    exit /b 1
)

echo [SUCCESS] Tim thay IP LAN: %SERVER_IP%
echo.

REM Hien thi IP cu (neu co)
echo [Step 2] Dang kiem tra IP hien tai trong cac client scripts...
set "OLD_IP="
for %%f in (START-CLIENT-*.bat) do (
    for /f "tokens=2 delims==" %%a in ('findstr /C:"set SERVER_IP=" "%%f"') do (
        set "OLD_IP=%%a"
        goto :found_old
    )
)
:found_old

if defined OLD_IP (
    echo [INFO] IP cu: %OLD_IP%
    if "%OLD_IP%"=="%SERVER_IP%" (
        echo [INFO] IP khong thay doi! Khong can cap nhat.
        echo.
        pause
        exit /b 0
    )
) else (
    echo [INFO] Khong tim thay IP cu trong scripts.
)
echo.

REM Cap nhat IP vao tat ca START-CLIENT-*.bat
echo [Step 3] Dang cap nhat IP vao cac client scripts...
set "UPDATED_COUNT=0"

REM Su dung PowerShell de thay the (chinh xac hon)
for %%f in (START-CLIENT-*.bat) do (
    echo [INFO] Dang cap nhat: %%f
    
    powershell -Command "(Get-Content '%%f') -replace 'set SERVER_IP=.*', 'set SERVER_IP=%SERVER_IP%' | Set-Content '%%f'" >nul 2>&1
    
    if !errorlevel! equ 0 (
        echo [SUCCESS] Da cap nhat: %%f
        set /a UPDATED_COUNT+=1
    ) else (
        echo [WARNING] Co the khong cap nhat duoc: %%f
        echo [INFO] Thu cap nhat thu cong...
    )
)

echo.
echo ========================================
echo   [HOAN TAT] Cap nhat IP thanh cong!
echo ========================================
echo.
echo [THONG TIN]
echo   IP LAN moi: %SERVER_IP%
if defined OLD_IP (
    echo   IP LAN cu: %OLD_IP%
)
echo   So file da cap nhat: %UPDATED_COUNT%
echo.
echo [DANH SACH FILE DA CAP NHAT]
for %%f in (START-CLIENT-*.bat) do (
    echo   - %%f
)
echo.
echo [BƯỚC TIẾP THEO]
echo   1. Cap nhat IP nay vao cac client scripts tren may khac (neu co)
echo   2. Chay server: START-SERVER.bat hoac QUICK-START.bat
echo   3. Chay client: START-CLIENT-*.bat
echo.
pause

