@echo off
title RTP AV Conference - CLIENT
color 0B
cls
echo.
echo ========================================
echo   RTP AV Conference - CLIENT
echo   [DANG CHAY - RUNNING]
echo ========================================
echo.
cd /d "%~dp0"

REM Check Java
set JAVA_HOME=C:\Program Files\Eclipse Adoptium\jdk-21.0.8.9-hotspot
if not exist "%JAVA_HOME%\bin\java.exe" (
    echo ERROR: Java 21 not found at %JAVA_HOME%
    echo Please check Java installation.
    pause
    exit /b 1
)

REM Check Maven
set MAVEN_PATH=%USERPROFILE%\apache-maven-3.9.6\bin\mvn.cmd
if not exist "%MAVEN_PATH%" (
    echo ERROR: Maven not found at %MAVEN_PATH%
    echo Please check Maven installation.
    pause
    exit /b 1
)

echo [***] DANG KHOI DONG CLIENT... [***]
echo [***] VUI LONG DOI... [***]
echo.
echo ========================================
echo   THONG TIN KET NOI:
echo ========================================
echo [INFO] Java: %JAVA_HOME%
echo [INFO] Maven: %MAVEN_PATH%
echo [INFO] Server: localhost
echo [INFO] Ten: User
echo [INFO] Room: demo
echo.
echo ========================================
echo   TRANG THAI: DANG CHAY...
echo ========================================
echo.
echo [CHU Y] Cua so nay phai MO de client chay!
echo [CHU Y] Cua so Client UI se MO sau vai giay!
echo.
echo ========================================
echo   LOG CLIENT:
echo ========================================
echo.

"%MAVEN_PATH%" -pl rtp-av-client exec:java -Dexec.mainClass="com.example.rtpav.client.ClientMain" -Dexec.args="--server localhost --name User --room demo"
if errorlevel 1 (
    echo.
    echo ========================================
    echo   [ERROR] Failed to start client!
    echo ========================================
    echo.
    echo [TROUBLESHOOTING]
    echo 1. Make sure server is running first
    echo 2. Check if you see "RMI ready" in server window
    echo 3. Check the error messages above
    echo.
    pause
    exit /b 1
)
pause

