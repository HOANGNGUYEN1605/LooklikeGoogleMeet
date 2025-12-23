@echo off
chcp 65001 >nul
cd /d "%~dp0"
javac test-hash-password.java
if %ERRORLEVEL% EQU 0 (
    java TestHashPassword
)
pause

