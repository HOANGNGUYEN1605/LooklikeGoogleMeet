@echo off
chcp 65001 >nul
echo ========================================
echo Quick Start - Khởi động nhanh
echo ========================================
echo.
echo Script này sẽ:
echo   1. Kiểm tra và khởi động MySQL container
echo   2. Khởi động server
echo   3. Sẵn sàng để chạy client
echo.

REM Kiểm tra Docker
docker ps >nul 2>&1
if %errorlevel% neq 0 (
    echo ERROR: Docker không chạy!
    echo Vui lòng mở Docker Desktop và chạy lại.
    pause
    exit /b 1
)

REM Kiểm tra và khởi động MySQL container
docker ps | findstr "rtp-mysql" >nul
if %errorlevel% neq 0 (
    echo MySQL container không chạy. Đang khởi động...
    docker start rtp-mysql >nul 2>&1
    if %errorlevel% neq 0 (
        echo ERROR: Không thể khởi động MySQL container!
        echo Vui lòng chạy: database\setup-docker-mysql.bat
        pause
        exit /b 1
    )
    echo Đợi MySQL khởi động...
    timeout /t 10 >nul
)

echo MySQL container đang chạy!
echo.

REM Khởi động server
echo Đang khởi động server...
echo.
start "RTP AV Conference - SERVER" cmd /k "START-SERVER.bat"

echo.
echo ========================================
echo Server đang khởi động!
echo ========================================
echo.
echo Đợi đến khi thấy "RMI ready" trong cửa sổ server.
echo Sau đó bạn có thể chạy client bằng:
echo   START-CLIENT-1.bat
echo   START-CLIENT-2.bat
echo   ... hoặc START-ALL-6-CLIENTS.bat
echo.
pause

