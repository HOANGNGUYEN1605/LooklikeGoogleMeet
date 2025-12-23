@echo off
title RTP AV Conference - CLIENT 3 (Charlie)
color 0E
cls
echo.
echo ========================================
echo   RTP AV Conference - CLIENT 3
echo   Name: Charlie, Port: 6002
echo ========================================
echo.
cd /d "%~dp0"

set JAVA_HOME=C:\Program Files\Eclipse Adoptium\jdk-21.0.8.9-hotspot
set MAVEN_PATH=%USERPROFILE%\apache-maven-3.9.6\bin\mvn.cmd

call "%MAVEN_PATH%" -pl rtp-av-client exec:java -Dexec.mainClass=com.example.rtpav.client.ClientMain "-Dexec.args=--server localhost --name Charlie --room demo --rtp 6002"
pause


