@echo off
title RTP AV Conference - CLIENT (ZeroTier)
color 0B
cls
echo.
echo ========================================
echo   RTP AV Conference - CLIENT (ZeroTier)
echo ========================================
echo.
echo [CHU Y] Ban can:
echo   1. Da cai dat va chay ZeroTier
echo   2. Da tham gia ZeroTier Network
echo   3. Da duoc authorize trong ZeroTier Central
echo   4. Co Server ZeroTier IP
echo.
echo ========================================
echo.

cd /d "%~dp0"

REM ========================================
REM THAY DOI THONG TIN SAU:
REM ========================================
REM Server ZeroTier IP (vi du: 10.147.20.100)
set SERVER_IP=10.147.20.100

REM Ten hien thi cua ban (vi du: Alice, Bob, ...)
set CLIENT_NAME=YourName

REM Ten phong (vi du: demo, room1, ...)
set ROOM_NAME=demo

REM Port RTP local (moi client can port khac nhau: 6000, 6001, 6002, ...)
set RTP_PORT=6000

REM ========================================
REM KHONG SUA PHAN NAY
REM ========================================

set JAVA_HOME=C:\Program Files\Eclipse Adoptium\jdk-21.0.8.9-hotspot
set MAVEN_PATH=%USERPROFILE%\apache-maven-3.9.6\bin\mvn.cmd

echo [INFO] Server IP: %SERVER_IP%
echo [INFO] Client Name: %CLIENT_NAME%
echo [INFO] Room: %ROOM_NAME%
echo [INFO] RTP Port: %RTP_PORT%
echo.
echo [INFO] Dang ket noi den server...
echo.

call "%MAVEN_PATH%" -pl rtp-av-client exec:java -Dexec.mainClass=com.example.rtpav.client.ClientMain "-Dexec.args=--server %SERVER_IP% --name %CLIENT_NAME% --room %ROOM_NAME% --rtp %RTP_PORT%"
pause

