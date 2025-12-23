@echo off
chcp 65001 >nul
echo ========================================
echo Fix Database Connection
echo ========================================
echo.
echo Script này sẽ kiểm tra và fix vấn đề kết nối database
echo sau khi tạo lại Docker container.
echo.

REM Kiểm tra Docker đang chạy
echo [Step 1] Checking Docker...
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

REM Kiểm tra container có tồn tại không
echo [Step 2] Checking MySQL container...
docker ps -a | findstr "rtp-mysql" >nul
if %errorlevel% neq 0 (
    echo.
    echo ERROR: Container rtp-mysql không tồn tại!
    echo.
    echo Vui lòng chạy setup-docker-mysql.bat trước.
    echo.
    pause
    exit /b 1
)

echo Container found!
echo.

REM Kiểm tra container có đang chạy không
echo [Step 3] Checking if container is running...
docker ps | findstr "rtp-mysql" >nul
if %errorlevel% neq 0 (
    echo Container is not running. Starting it...
    docker start rtp-mysql
    if %errorlevel% neq 0 (
        echo.
        echo ERROR: Failed to start container!
        pause
        exit /b 1
    )
    echo Container started!
    echo.
    echo Waiting for MySQL to be ready...
    timeout /t 5 >nul
) else (
    echo Container is running!
    echo.
)

REM Đợi MySQL khởi động hoàn toàn
echo [Step 4] Waiting for MySQL to be ready...
:wait_loop
docker exec rtp-mysql mysqladmin ping -h localhost --silent >nul 2>&1
if %errorlevel% neq 0 (
    echo Waiting for MySQL...
    timeout /t 3 >nul
    goto :wait_loop
)

echo MySQL is ready!
echo.

REM Kiểm tra xem database có tồn tại không
echo [Step 5] Checking database...
REM Thử kết nối từ host thay vì từ trong container
where mysql >nul 2>&1
if %errorlevel% equ 0 (
    echo Trying to connect from host...
    mysql -h 127.0.0.1 -P 3306 -u root -e "USE rtp_conference;" >nul 2>&1
    if %errorlevel% neq 0 (
        echo Database rtp_conference không tồn tại hoặc không thể truy cập!
        echo.
        echo Tạo lại database từ host...
        mysql -h 127.0.0.1 -P 3306 -u root -e "CREATE DATABASE IF NOT EXISTS rtp_conference CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;" 2>nul
        if %errorlevel% neq 0 (
            echo Trying alternative method...
            REM Thử với docker exec
            docker exec rtp-mysql sh -c "mysql -u root -e 'CREATE DATABASE IF NOT EXISTS rtp_conference CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;' 2>&1" >nul 2>&1
        )
        echo Database check completed!
        echo.
    ) else (
        echo Database exists!
        echo.
    )
) else (
    echo MySQL client not found, trying docker exec method...
    docker exec rtp-mysql sh -c "mysql -u root -e 'USE rtp_conference;' 2>&1" >nul 2>&1
    if %errorlevel% neq 0 (
        echo Database rtp_conference không tồn tại hoặc không thể truy cập!
        echo.
        echo Tạo lại database...
        docker exec rtp-mysql sh -c "mysql -u root -e 'CREATE DATABASE IF NOT EXISTS rtp_conference CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;' 2>&1" >nul 2>&1
        echo Database check completed!
        echo.
    ) else (
        echo Database exists!
        echo.
    )
)

REM Kiểm tra xem có bảng users không
echo [Step 6] Checking tables...
REM Thử từ host trước
where mysql >nul 2>&1
if %errorlevel% equ 0 (
    mysql -h 127.0.0.1 -P 3306 -u root rtp_conference -e "SHOW TABLES LIKE 'users';" 2>nul | findstr "users" >nul
    if %errorlevel% neq 0 (
        echo Table 'users' không tồn tại!
        echo.
        echo Setting up database schema from host...
        echo.
        
        REM Chạy schema.sql từ host
        cd /d "%~dp0"
        mysql -h 127.0.0.1 -P 3306 -u root rtp_conference < schema.sql 2>nul
        
        if %errorlevel% equ 0 (
            echo Schema setup completed!
            echo.
        ) else (
            echo Trying docker exec method...
            docker exec -i rtp-mysql mysql -u root rtp_conference < schema.sql 2>nul
            if %errorlevel% equ 0 (
                echo Schema setup completed!
                echo.
            ) else (
                echo WARNING: Schema setup may have failed.
                echo.
            )
        )
    ) else (
        echo Table 'users' exists!
        echo.
    )
) else (
    echo MySQL client not found, using docker exec method...
    docker exec rtp-mysql sh -c "mysql -u root rtp_conference -e \"SHOW TABLES LIKE 'users';\" 2>&1" | findstr "users" >nul
    if %errorlevel% neq 0 (
        echo Table 'users' không tồn tại!
        echo.
        echo Setting up database schema...
        echo.
        
        REM Chạy schema.sql
        cd /d "%~dp0"
        docker exec -i rtp-mysql mysql -u root rtp_conference < schema.sql 2>nul
        
        if %errorlevel% equ 0 (
            echo Schema setup completed!
            echo.
        ) else (
            echo WARNING: Schema setup may have failed.
            echo Please check if you can connect to MySQL.
            echo.
        )
    ) else (
        echo Table 'users' exists!
        echo.
    )
)

REM Kiểm tra xem có user admin không
echo [Step 7] Checking for admin user...
docker exec rtp-mysql mysql -u root rtp_conference -e "SELECT COUNT(*) as count FROM users WHERE username='admin';" 2>nul | findstr "1" >nul
if %errorlevel% neq 0 (
    echo Admin user không tồn tại!
    echo.
    echo Creating admin user...
    docker exec -i rtp-mysql mysql -u root rtp_conference -e "INSERT INTO users (username, password, display_name, email) VALUES ('admin', '240be518fabd2724ddb6f04eeb1da5967448d7e831c08c8fa822809f74c720a9', 'Administrator', 'admin@example.com') ON DUPLICATE KEY UPDATE username=username;"
    
    if %errorlevel% equ 0 (
        echo Admin user created!
        echo.
    ) else (
        echo WARNING: Failed to create admin user.
        echo.
    )
) else (
    echo Admin user exists!
    echo.
)

REM Test connection
echo [Step 8] Testing database connection...
docker exec rtp-mysql mysql -u root rtp_conference -e "SELECT COUNT(*) as user_count FROM users;" 2>nul
if %errorlevel% equ 0 (
    echo.
    echo ========================================
    echo Database connection test: SUCCESS!
    echo ========================================
    echo.
    echo Database is ready to use!
    echo.
    echo Connection info:
    echo   Host: localhost
    echo   Port: 3306
    echo   Database: rtp_conference
    echo   User: root
    echo   Password: (empty)
    echo.
    echo Default login:
    echo   Username: admin
    echo   Password: admin123
    echo.
) else (
    echo.
    echo ========================================
    echo WARNING: Database connection test failed!
    echo ========================================
    echo.
    echo Please check the error messages above.
    echo.
)

echo ========================================
echo Fix completed!
echo ========================================
echo.
pause

