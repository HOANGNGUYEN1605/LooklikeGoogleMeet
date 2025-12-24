@echo off
setlocal enabledelayedexpansion
title Cap Nhat IP Server tren May Client
color 0B
cls
echo.
echo ========================================
echo   CAP NHAT IP SERVER TREN MAY CLIENT
echo   RTP AV Conference
echo ========================================
echo.
echo Script nay se:
echo   1. Hoi ban IP LAN cua SERVER
echo   2. Cap nhat IP vao tat ca START-CLIENT-*.bat
echo   3. Hien thi ket qua
echo.
echo [CHU Y] Chay script nay sau khi SERVER doi mang
echo [CHU Y] Can biet IP LAN moi cua SERVER
echo.
pause

cd /d "%~dp0"

REM Hoi IP LAN cua SERVER
echo.
echo ========================================
echo   NHAP IP LAN CUA SERVER
echo ========================================
echo.
echo [HUONG DAN]
echo   1. Tren may SERVER, chay: TIM-IP-LAN.bat
echo   2. Ghi lai IP LAN (vi du: 192.168.1.100)
echo   3. Nhap IP do vao day
echo.
set /p SERVER_IP="Nhap IP LAN cua SERVER: "

if not defined SERVER_IP (
    echo [ERROR] Ban chua nhap IP!
    pause
    exit /b 1
)

REM Kiem tra dinh dang IP
echo %SERVER_IP% | findstr /R "^[0-9][0-9]*\.[0-9][0-9]*\.[0-9][0-9]*\.[0-9][0-9]*$" >nul
if errorlevel 1 (
    echo [ERROR] IP khong hop le! Dinh dang: xxx.xxx.xxx.xxx
    pause
    exit /b 1
)

echo.
echo [INFO] IP LAN cua SERVER: %SERVER_IP%
echo.

REM Hien thi IP cu (neu co)
echo [Step 1] Dang kiem tra IP hien tai trong cac client scripts...
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
echo [Step 2] Dang cap nhat IP vao cac client scripts...
set "UPDATED_COUNT=0"

for %%f in (START-CLIENT-*.bat) do (
    echo [INFO] Dang cap nhat: %%f
    
    powershell -Command "(Get-Content '%%f') -replace 'set SERVER_IP=.*', 'set SERVER_IP=%SERVER_IP%' | Set-Content '%%f'" >nul 2>&1
    
    if !errorlevel! equ 0 (
        echo [SUCCESS] Da cap nhat: %%f
        set /a UPDATED_COUNT+=1
    ) else (
        echo [WARNING] Co the khong cap nhat duoc: %%f
    )
)

echo.
echo ========================================
echo   [HOAN TAT] Cap nhat IP thanh cong!
echo ========================================
echo.
echo [THONG TIN]
echo   IP LAN moi cua SERVER: %SERVER_IP%
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
echo   1. Dam bao SERVER da chay: START-SERVER.bat tren may SERVER
echo   2. Chay client: START-CLIENT-*.bat
echo.
pause

