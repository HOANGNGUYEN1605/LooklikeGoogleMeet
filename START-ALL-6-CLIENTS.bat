@echo off
title RTP AV Conference - Launch All 6 Clients
color 0F
cls
echo.
echo ========================================
echo   RTP AV Conference
echo   Launching All 6 Clients...
echo ========================================
echo.
echo This will start 6 client windows:
echo   - Client 1: Alice (Port 6000)
echo   - Client 2: Bob (Port 6001)
echo   - Client 3: Charlie (Port 6002)
echo   - Client 4: David (Port 6003)
echo   - Client 5: Eve (Port 6004)
echo   - Client 6: Frank (Port 6005)
echo.
echo Make sure the SERVER is running first!
echo.
pause

cd /d "%~dp0"

start "Client 1 - Alice" cmd /c "START-CLIENT-1.bat"
timeout /t 2 /nobreak >nul

start "Client 2 - Bob" cmd /c "START-CLIENT-2.bat"
timeout /t 2 /nobreak >nul

start "Client 3 - Charlie" cmd /c "START-CLIENT-3.bat"
timeout /t 2 /nobreak >nul

start "Client 4 - David" cmd /c "START-CLIENT-4.bat"
timeout /t 2 /nobreak >nul

start "Client 5 - Eve" cmd /c "START-CLIENT-5.bat"
timeout /t 2 /nobreak >nul

start "Client 6 - Frank" cmd /c "START-CLIENT-6.bat"

echo.
echo All 6 clients have been launched!
echo Each client will open in a separate window.
echo.
pause








