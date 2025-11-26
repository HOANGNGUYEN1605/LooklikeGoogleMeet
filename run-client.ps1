# Script to run RTP AV Conference Client
param(
    [string]$server = "localhost",
    [string]$name = "User",
    [string]$room = "demo"
)

$env:JAVA_HOME = "C:\Program Files\Eclipse Adoptium\jdk-21.0.8.9-hotspot"
$mavenPath = "$env:USERPROFILE\apache-maven-3.9.6\bin\mvn.cmd"

Write-Host "Starting RTP AV Conference Client..." -ForegroundColor Green
Write-Host "Server: $server, Name: $name, Room: $room" -ForegroundColor Yellow
cd "D:\Program Files\WorkSpace\IdeaProjects\rtp-av-conference"

& $mavenPath -pl rtp-av-client exec:java -Dexec.mainClass="com.example.rtpav.client.ClientMain" -Dexec.args="--server $server --name $name --room $room"

