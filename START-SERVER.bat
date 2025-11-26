@echo off
title RTP AV Conference - SERVER
color 0A
cls
echo.
echo ========================================
echo   RTP AV Conference - SERVER
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

echo [***] DANG KHOI DONG SERVER... [***]
echo [***] VUI LONG DOI... [***]
echo.
echo ========================================
echo   THONG TIN HE THONG:
echo ========================================
echo [INFO] Java: %JAVA_HOME%
echo [INFO] Maven: %MAVEN_PATH%
echo.
echo ========================================
echo   TRANG THAI: DANG CHAY...
echo ========================================
echo.
echo [CHU Y] Cua so nay phai MO de server chay!
echo [CHU Y] Tim dong "RMI ready" ben duoi!
echo.
echo ========================================
echo   LOG SERVER (Tim "RMI ready"):
echo ========================================
echo.

"%MAVEN_PATH%" -pl rtp-av-server exec:java -Dexec.mainClass="com.example.rtpav.server.ServerMain"
if errorlevel 1 (
    echo.
    echo ========================================
    echo   [ERROR] Failed to start server!
    echo ========================================
    echo.
    echo Please check the error messages above.
    pause
    exit /b 1
)
pause

