@echo off
title Fix Client Ket Noi Server
color 0E
cls
echo.
echo ========================================
echo   FIX CLIENT KET NOI SERVER
echo   RTP AV Conference
echo ========================================
echo.
echo Script nay se:
echo   1. Xoa target directories (clean build)
echo   2. Rebuild client
echo   3. Kiem tra IP trong START-CLIENT-*.bat
echo.
pause

cd /d "%~dp0"

REM Kiem tra IP trong START-CLIENT-*.bat
echo.
echo [Step 1] Kiem tra IP trong START-CLIENT-*.bat...
echo.
set "FOUND_WRONG_IP=0"
for %%f in (START-CLIENT-*.bat) do (
    findstr /C:"set SERVER_IP=" "%%f" | findstr /V "192.168.50.129" >nul
    if !errorlevel! equ 0 (
        echo [WARNING] File %%f co IP khac 192.168.50.129
        findstr /C:"set SERVER_IP=" "%%f"
        set /a FOUND_WRONG_IP+=1
    )
)

if %FOUND_WRONG_IP% gtr 0 (
    echo.
    echo [ERROR] Tim thay %FOUND_WRONG_IP% file(s) co IP sai!
    echo [ERROR] Vui long cap nhat IP trong cac file nay truoc!
    echo.
    pause
    exit /b 1
)

echo [SUCCESS] Tat ca START-CLIENT-*.bat co IP dung: 192.168.50.129
echo.

REM Xoa target directories
echo [Step 2] Dang xoa target directories...
if exist rtp-av-client\target (
    rmdir /s /q rtp-av-client\target
    echo [SUCCESS] Da xoa rtp-av-client\target
)
if exist rtp-rmi-common\target (
    rmdir /s /q rtp-rmi-common\target
    echo [SUCCESS] Da xoa rtp-rmi-common\target
)
echo.

REM Rebuild client
echo [Step 3] Dang rebuild client...
echo [INFO] Qua trinh nay co the mat 1-2 phut...
echo.

set JAVA_HOME=C:\Program Files\Eclipse Adoptium\jdk-21.0.8.9-hotspot
set MAVEN_PATH=%USERPROFILE%\apache-maven-3.9.6\bin\mvn.cmd

if not exist "%JAVA_HOME%\bin\java.exe" (
    echo [ERROR] Java khong tim thay tai: %JAVA_HOME%
    echo [ERROR] Vui long cap nhat JAVA_HOME trong script nay!
    pause
    exit /b 1
)

if not exist "%MAVEN_PATH%" (
    echo [ERROR] Maven khong tim thay tai: %MAVEN_PATH%
    echo [ERROR] Vui long cap nhat MAVEN_PATH trong script nay!
    pause
    exit /b 1
)

set PATH=%JAVA_HOME%\bin;%PATH%

call "%MAVEN_PATH%" clean install -pl rtp-rmi-common,rtp-av-client -am -DskipTests

if errorlevel 1 (
    echo.
    echo [ERROR] Build that bai!
    echo [ERROR] Vui long kiem tra loi ben tren.
    echo.
    pause
    exit /b 1
)

echo.
echo ========================================
echo   [SUCCESS] Rebuild thanh cong!
echo ========================================
echo.
echo [BƯỚC TIẾP THEO]
echo   1. Dam bao SERVER da chay tren may khac
echo   2. Chay client: START-CLIENT-*.bat
echo.
pause

