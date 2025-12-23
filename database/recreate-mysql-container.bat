@echo off
chcp 65001 >nul
echo ========================================
echo Recreate MySQL Container
echo ========================================
echo.
echo Script này sẽ xóa container cũ và tạo lại container mới
echo với database và schema đã được setup sẵn.
echo.

REM Kiểm tra Docker đang chạy
docker ps >nul 2>&1
if %errorlevel% neq 0 (
    echo.
    echo ERROR: Docker daemon không chạy!
    echo.
    echo Vui lòng:
    echo   1. Mở Docker Desktop
    echo   2. Đợi Docker khởi động xong
    echo   3. Chạy lại script này
    echo.
    pause
    exit /b 1
)

echo Docker is running!
echo.

REM Dừng và xóa container cũ nếu có
echo [Step 1] Stopping and removing old container...
docker ps -a | findstr "rtp-mysql" >nul
if %errorlevel% equ 0 (
    echo Stopping container...
    docker stop rtp-mysql >nul 2>&1
    echo Removing container...
    docker rm rtp-mysql >nul 2>&1
    echo Old container removed!
    echo.
) else (
    echo No existing container found.
    echo.
)

REM Tạo container mới
echo [Step 2] Creating new MySQL container...
echo.
echo Container name: rtp-mysql
echo Port: 3306
echo Root password: (empty - no password)
echo Database: rtp_conference
echo.
echo Starting container...
docker run -d ^
    --name rtp-mysql ^
    -p 3306:3306 ^
    -e MYSQL_ROOT_PASSWORD= ^
    -e MYSQL_ALLOW_EMPTY_PASSWORD=yes ^
    -e MYSQL_DATABASE=rtp_conference ^
    mysql:8.0

if %errorlevel% neq 0 (
    echo.
    echo ERROR: Failed to create container!
    pause
    exit /b 1
)

echo Container created successfully!
echo.

REM Đợi MySQL khởi động
echo [Step 3] Waiting for MySQL to start...
echo This may take 30-60 seconds...
timeout /t 10 >nul

:wait_loop
docker exec rtp-mysql mysqladmin ping -h localhost --silent >nul 2>&1
if %errorlevel% neq 0 (
    echo Waiting for MySQL...
    timeout /t 5 >nul
    goto :wait_loop
)

echo MySQL is ready!
echo.

REM Đợi thêm một chút để MySQL hoàn toàn sẵn sàng
echo [Step 4] Waiting for MySQL to be fully ready...
timeout /t 10 >nul

REM Đảm bảo database được tạo
echo [Step 5] Ensuring database exists...
docker exec rtp-mysql sh -c "mysql -u root -e 'CREATE DATABASE IF NOT EXISTS rtp_conference CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;' 2>&1"
if %errorlevel% neq 0 (
    echo WARNING: Could not create database, but it might already exist.
    echo.
)

REM Chạy schema
echo [Step 6] Setting up database schema...
cd /d "%~dp0"

REM Copy schema vào container và chạy từ trong container
docker cp schema.sql rtp-mysql:/tmp/schema.sql
docker exec rtp-mysql sh -c "mysql -u root rtp_conference < /tmp/schema.sql 2>&1"

if %errorlevel% equ 0 (
    echo.
    echo ========================================
    echo Setup completed successfully!
    echo ========================================
    echo.
    echo MySQL is running in Docker container: rtp-mysql
    echo Connection: localhost:3306
    echo Database: rtp_conference
    echo User: root
    echo Password: (empty)
    echo.
    echo Default login:
    echo   Username: admin
    echo   Password: admin123
    echo.
    
    REM Kiểm tra user admin
    echo [Step 7] Verifying admin user...
    docker exec rtp-mysql sh -c "mysql -u root rtp_conference -e \"SELECT username, display_name FROM users WHERE username='admin';\" 2>&1"
    if %errorlevel% equ 0 (
        echo.
        echo Admin user verified!
        echo.
    ) else (
        echo.
        echo WARNING: Could not verify admin user.
        echo.
    )
    
) else (
    echo.
    echo WARNING: Schema setup may have failed.
    echo You can run it manually:
    echo   docker exec -i rtp-mysql mysql -u root rtp_conference ^< schema.sql
    echo.
)

echo ========================================
echo Container recreation completed!
echo ========================================
echo.
echo You can now start your server.
echo.
pause

