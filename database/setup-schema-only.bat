@echo off
chcp 65001 >nul
echo ========================================
echo Setup Database Schema Only
echo ========================================
echo.
echo Script này sẽ chạy schema.sql vào container hiện tại.
echo.

REM Kiểm tra container có đang chạy không
docker ps | findstr "rtp-mysql" >nul
if %errorlevel% neq 0 (
    echo ERROR: Container rtp-mysql không đang chạy!
    echo Vui lòng chạy: docker start rtp-mysql
    pause
    exit /b 1
)

echo Container đang chạy!
echo.

REM Đợi MySQL sẵn sàng
echo Waiting for MySQL to be ready...
:wait_loop
docker exec rtp-mysql mysqladmin ping -h localhost --silent >nul 2>&1
if %errorlevel% neq 0 (
    echo Waiting for MySQL...
    timeout /t 3 >nul
    goto :wait_loop
)

echo MySQL is ready!
echo.

REM Thử nhiều cách để chạy schema
echo [Method 1] Trying to run schema from host (if MySQL client available)...
where mysql >nul 2>&1
if %errorlevel% equ 0 (
    echo MySQL client found, trying to connect from host...
    cd /d "%~dp0"
    mysql -h 127.0.0.1 -P 3306 -u root rtp_conference < schema.sql 2>nul
    if %errorlevel% equ 0 (
        echo Schema setup completed using host MySQL client!
        goto :success
    ) else (
        echo Host connection failed, trying docker exec method...
    )
) else (
    echo MySQL client not found, using docker exec method...
)

echo.
echo [Method 2] Trying to run schema using docker exec...
cd /d "%~dp0"

REM Copy schema vào container
docker cp schema.sql rtp-mysql:/tmp/schema.sql

REM Chạy schema từ trong container
docker exec rtp-mysql sh -c "mysql -u root rtp_conference < /tmp/schema.sql 2>&1"

if %errorlevel% equ 0 (
    echo Schema setup completed using docker exec!
    goto :success
) else (
    echo.
    echo [Method 3] Trying alternative connection method...
    REM Thử với cách khác - có thể cần password
    docker exec rtp-mysql sh -c "mysql -u root --password='' rtp_conference < /tmp/schema.sql 2>&1"
    if %errorlevel% equ 0 (
        echo Schema setup completed!
        goto :success
    )
)

echo.
echo ========================================
echo ERROR: Failed to setup schema!
echo ========================================
echo.
echo Please try one of these solutions:
echo.
echo 1. Recreate container:
echo    cd database
echo    recreate-mysql-container.bat
echo.
echo 2. Or run schema manually:
echo    docker exec -it rtp-mysql mysql -u root rtp_conference
echo    Then copy and paste contents of schema.sql
echo.
pause
exit /b 1

:success
echo.
echo ========================================
echo Schema setup completed successfully!
echo ========================================
echo.
echo Verifying admin user...
docker exec rtp-mysql sh -c "mysql -u root rtp_conference -e \"SELECT username, display_name FROM users WHERE username='admin';\" 2>&1" | findstr "admin" >nul
if %errorlevel% equ 0 (
    echo Admin user verified!
) else (
    echo WARNING: Could not verify admin user.
)
echo.
echo Database is ready to use!
echo.
pause




