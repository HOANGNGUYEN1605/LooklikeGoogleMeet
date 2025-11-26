@echo off
title RTP AV Conference - CLIENT 1 (Alice)
color 0B
cls
echo.
echo ========================================
echo   RTP AV Conference - CLIENT 1
echo   Name: Alice, Port: 6000
echo ========================================
echo.
cd /d "%~dp0"

set JAVA_HOME=C:\Program Files\Eclipse Adoptium\jdk-21.0.8.9-hotspot
set MAVEN_PATH=%USERPROFILE%\apache-maven-3.9.6\bin\mvn.cmd

"%MAVEN_PATH%" -pl rtp-av-client exec:java -Dexec.mainClass="com.example.rtpav.client.ClientMain" -Dexec.args="--server localhost --name Alice --room demo --rtp 6000"
pause


