@echo off
title Rebuild and Restart Server
color 0E
cls
echo.
echo ========================================
echo   REBUILD AND RESTART SERVER
echo ========================================
echo.
echo Script nay se:
echo   1. Rebuild server (mvn clean install)
echo   2. Restart server
echo.
echo [CHU Y] Dung tat ca client truoc khi chay script nay!
echo.
pause

cd /d "%~dp0"

REM Set Java Home
set JAVA_HOME=C:\Program Files\Eclipse Adoptium\jdk-21.0.8.9-hotspot
if not exist "%JAVA_HOME%\bin\java.exe" (
    echo ERROR: Java 21 not found at %JAVA_HOME%
    echo Please check Java installation.
    pause
    exit /b 1
)

REM Set JAVA_HOME for Maven
set PATH=%JAVA_HOME%\bin;%PATH%

REM Check Maven
set MAVEN_PATH=%USERPROFILE%\apache-maven-3.9.6\bin\mvn.cmd
if not exist "%MAVEN_PATH%" (
    echo ERROR: Maven not found at %MAVEN_PATH%
    echo Please check Maven installation.
    pause
    exit /b 1
)

echo.
echo ========================================
echo   STEP 1: REBUILDING SERVER
echo ========================================
echo [INFO] Cleaning and rebuilding server...
echo [INFO] This may take 1-2 minutes...
echo.

call "%MAVEN_PATH%" clean install -pl rtp-av-server -am -DskipTests

if errorlevel 1 (
    echo.
    echo ========================================
    echo   [ERROR] Build failed!
    echo ========================================
    echo.
    pause
    exit /b 1
)

echo.
echo ========================================
echo   [SUCCESS] Build completed!
echo ========================================
echo.
echo ========================================
echo   STEP 2: STARTING SERVER
echo ========================================
echo [INFO] Starting server with new code...
echo [INFO] Look for new logs:
echo [INFO]   - [SERVER] RTP Forwarder: Server LAN IP = ...
echo [INFO]   - [SERVER] RMI Service: Server LAN IP = ...
echo.
echo ========================================
echo   SERVER LOGS:
echo ========================================
echo.

call "%MAVEN_PATH%" -pl rtp-av-server exec:java

if errorlevel 1 (
    echo.
    echo ========================================
    echo   [ERROR] Failed to start server!
    echo ========================================
    echo.
    pause
    exit /b 1
)

pause

