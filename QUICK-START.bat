@echo off
chcp 65001 >nul
title Quick Start - RTP AV Conference Server
color 0A
cls
echo.
echo ========================================
echo   QUICK START - KHOI DONG NHANH
echo   RTP AV Conference Server
echo ========================================
echo.
echo Script nay se:
echo   1. Kiem tra Docker dang chay
echo   2. Khoi dong MySQL container (neu can)
echo   3. Khoi dong server
echo   4. San sang de chay client
echo.
echo [CHU Y] Firewall ports da mo vi vien!
echo [CHU Y] Khong can mo lai firewall nua!
echo.
pause

REM Kiểm tra Docker
echo [Step 1] Kiem tra Docker...
docker ps >nul 2>&1
if %errorlevel% neq 0 (
    echo.
    echo [ERROR] Docker khong chay!
    echo [ERROR] Vui long mo Docker Desktop va cho no khoi dong xong!
    echo.
    echo [HUONG DAN]
    echo   1. Mo Docker Desktop tu Start Menu
    echo   2. Doi den khi thay "Docker Desktop is running"
    echo   3. Chay lai script nay
    echo.
    pause
    exit /b 1
)
echo [SUCCESS] Docker dang chay!
echo.

REM Kiểm tra và khởi động MySQL container
echo [Step 2] Kiem tra MySQL container...
docker ps | findstr "rtp-mysql" >nul
if %errorlevel% neq 0 (
    echo [INFO] MySQL container khong chay. Dang khoi dong...
    docker start rtp-mysql >nul 2>&1
    if %errorlevel% neq 0 (
        echo.
        echo [ERROR] Khong the khoi dong MySQL container!
        echo [ERROR] Container co the chua duoc tao.
        echo.
        echo [HUONG DAN]
        echo   Chay: database\setup-docker-mysql.bat
        echo   (Chi can lam 1 lan dau tien)
        echo.
        pause
        exit /b 1
    )
    echo [INFO] Doi MySQL khoi dong (10 giay)...
    timeout /t 10 /nobreak >nul
    
    REM Kiểm tra MySQL đã sẵn sàng chưa
    :wait_mysql
    docker exec rtp-mysql mysqladmin ping -h localhost --silent >nul 2>&1
    if %errorlevel% neq 0 (
        echo [INFO] Dang doi MySQL...
        timeout /t 3 /nobreak >nul
        goto :wait_mysql
    )
    echo [SUCCESS] MySQL da san sang!
) else (
    echo [SUCCESS] MySQL container dang chay!
)
echo.

REM Khởi động server
echo [Step 3] Dang khoi dong server...
echo.
start "RTP AV Conference - SERVER" cmd /k "START-SERVER.bat"

echo.
echo ========================================
echo   [SUCCESS] Server dang khoi dong!
echo ========================================
echo.
echo [CHU Y] Doi den khi thay "RMI ready" trong cua so server!
echo.
echo [BƯỚC TIẾP THEO]
echo   Sau khi server khoi dong xong, ban co the:
echo   - Chay client: START-CLIENT-1.bat
echo   - Hoac chay nhieu client: START-ALL-CLIENTS-MAY1.bat
echo.
echo [THÔNG TIN]
echo   - Firewall ports: DA MO (vi vien)
echo   - MySQL container: DANG CHAY
echo   - Server: DANG KHOI DONG
echo.
pause





