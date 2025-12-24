@echo off
title RTP AV Conference - CLIENT 4 (David)
color 0A
cls
echo.
echo ========================================
echo   RTP AV Conference - CLIENT 4
echo   Name: David, Port: 6003
echo ========================================
echo.
cd /d "%~dp0"

set JAVA_HOME=C:\Program Files\Eclipse Adoptium\jdk-21.0.8.9-hotspot
set MAVEN_PATH=%USERPROFILE%\apache-maven-3.9.6\bin\mvn.cmd

REM ========================================
REM CONFIGURATION - THAY DOI IP SERVER O DAY
REM ========================================
REM Thay IP ben duoi bang IP LAN thuc te cua SERVER
REM Chay TIM-IP-LAN.bat tren may SERVER de tim IP
set SERVER_IP=192.168.50.129

call "%MAVEN_PATH%" -pl rtp-av-client exec:java -Dexec.mainClass=com.example.rtpav.client.ClientMain "-Dexec.args=--server %SERVER_IP% --name David --room demo --rtp 6003"
pause



