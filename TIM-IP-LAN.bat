@echo off
title Tim IP LAN
color 0A
cls
echo.
echo ========================================
echo   TIM IP LAN CUA MAY
echo ========================================
echo.
echo Dang tim IP LAN (khong phai ZeroTier, VirtualBox, VMware)...
echo.

REM Tim IP LAN (192.168.x.x hoac 10.x.x.x, khong phai 172.24, 172.30, 192.168.56)
ipconfig | findstr /C:"IPv4" | findstr /V "172.24" | findstr /V "172.30" | findstr /V "192.168.56" | findstr /V "169.254"

echo.
echo ========================================
echo   HUONG DAN
echo ========================================
echo.
echo 1. Tim dong "IPv4 Address" co IP 192.168.x.x hoac 10.x.x.x
echo 2. Day la IP LAN cua may ban
echo 3. Su dung IP nay trong START-CLIENT-1.bat
echo.
echo Vi du: Neu thay "192.168.1.100" -> Su dung IP nay
echo.
pause


