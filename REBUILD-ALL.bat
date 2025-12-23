@echo off
title Rebuild All Modules
color 0E
cls
echo.
echo ========================================
echo   REBUILD ALL MODULES
echo ========================================
echo.
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

echo [INFO] Cleaning and rebuilding all modules...
echo [INFO] This will rebuild: rtp-rmi-common, rtp-av-server, rtp-av-client
echo.
echo ========================================
echo   SYSTEM INFO:
echo ========================================
echo [INFO] Java: %JAVA_HOME%
echo [INFO] Maven: %MAVEN_PATH%
echo.
echo ========================================
echo   BUILDING...
echo ========================================
echo.

call "%MAVEN_PATH%" clean install -DskipTests

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
echo IMPORTANT: Restart the server after rebuild!
echo.
pause

