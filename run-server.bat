@echo off
echo Starting RTP AV Conference Server...
cd /d "%~dp0"
cd rtp-av-server\target
java -cp "rtp-av-server-1.0.0.jar;..\..\rtp-rmi-common\target\rtp-rmi-common-1.0.0.jar" com.example.rtpav.server.ServerMain
pause

