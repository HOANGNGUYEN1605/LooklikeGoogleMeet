# Script to run RTP AV Conference Server
$env:JAVA_HOME = "C:\Program Files\Eclipse Adoptium\jdk-21.0.8.9-hotspot"
$mavenPath = "$env:USERPROFILE\apache-maven-3.9.6\bin\mvn.cmd"

Write-Host "Starting RTP AV Conference Server..." -ForegroundColor Green
cd "D:\Program Files\WorkSpace\IdeaProjects\rtp-av-conference"

& $mavenPath -pl rtp-av-server exec:java -Dexec.mainClass="com.example.rtpav.server.ServerMain"

