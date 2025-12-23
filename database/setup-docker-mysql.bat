@echo off
chcp 65001 >nul
echo ========================================
echo Setup MySQL với Docker
echo ========================================
echo.
echo Phương án này KHÔNG cần XAMPP!
echo MySQL sẽ chạy trong Docker container.
echo.

REM Kiểm tra Docker
echo [Step 1] Checking Docker...
docker --version >nul 2>&1
if %errorlevel% neq 0 (
    echo.
    echo ERROR: Docker chưa được cài đặt!
    echo.
    echo Vui lòng cài Docker Desktop từ:
    echo   https://www.docker.com/products/docker-desktop
    echo.
    echo Hoặc chọn phương án khác:
    echo   - setup-mysql-standalone.bat (Cài MySQL riêng)
    echo   - setup-mariadb.bat (Cài MariaDB)
    echo.
    pause
    exit /b 1
)

docker --version
echo Docker is installed!
echo.

REM Kiểm tra Docker đang chạy
echo [Step 2] Checking if Docker is running...
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

REM Kiểm tra container đã tồn tại chưa
echo [Step 3] Checking existing MySQL container...
docker ps -a | findstr "rtp-mysql" >nul
if %errorlevel% equ 0 (
    echo MySQL container đã tồn tại!
    echo.
    set /p RESTART="Bạn có muốn xóa và tạo lại? (Y/N): "
    if /i "!RESTART!"=="Y" (
        echo Stopping and removing old container...
        docker stop rtp-mysql >nul 2>&1
        docker rm rtp-mysql >nul 2>&1
        echo Done.
    ) else (
        echo Starting existing container...
        docker start rtp-mysql
        echo.
        echo Container started! Skipping database setup.
        echo.
        goto :skip_setup
    )
) else (
    echo No existing container found.
)
echo.

REM Tạo MySQL container
echo [Step 4] Creating MySQL container...
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
echo [Step 5] Waiting for MySQL to start...
echo This may take 30-60 seconds...
timeout /t 5 >nul

:wait_loop
docker exec rtp-mysql mysqladmin ping -h localhost --silent >nul 2>&1
if %errorlevel% neq 0 (
    echo Waiting for MySQL...
    timeout /t 3 >nul
    goto :wait_loop
)

echo MySQL is ready!
echo.

REM Chạy schema
echo [Step 6] Setting up database schema...
docker exec -i rtp-mysql mysql -u root rtp_conference < schema.sql

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
) else (
    echo.
    echo WARNING: Schema setup may have failed.
    echo You can run it manually:
    echo   docker exec -i rtp-mysql mysql -u root rtp_conference ^< schema.sql
    echo.
)

:skip_setup
echo ========================================
echo Useful commands:
echo ========================================
echo.
echo Start container:   docker start rtp-mysql
echo Stop container:    docker stop rtp-mysql
echo View logs:         docker logs rtp-mysql
echo Connect to MySQL:  docker exec -it rtp-mysql mysql -u root rtp_conference
echo Remove container:  docker stop rtp-mysql ^&^& docker rm rtp-mysql
echo.
pause



