@echo off
chcp 65001 >nul
echo ========================================
echo Setup lại sau khi bật máy
echo ========================================
echo.
echo Script này sẽ kiểm tra và setup lại các thành phần cần thiết
echo sau khi bạn bật máy lại.
echo.

REM Kiểm tra Git
echo [Step 1] Kiểm tra Git repository...
if exist ".git" (
    echo Git repository đã tồn tại.
    echo.
    echo Đang kiểm tra updates từ GitHub...
    git fetch origin
    git status
    echo.
) else (
    echo Git repository chưa có!
    echo.
    echo Bạn có muốn clone từ GitHub không?
    echo Repository: https://github.com/HOANGNGUYEN1605/LooklikeGoogleMeet.git
    echo.
    set /p CLONE="Clone repository? (Y/N): "
    if /i "!CLONE!"=="Y" (
        cd ..
        git clone https://github.com/HOANGNGUYEN1605/LooklikeGoogleMeet.git
        cd LooklikeGoogleMeet
        echo.
        echo Đã clone repository thành công!
        echo.
    ) else (
        echo Bỏ qua clone. Vui lòng clone thủ công sau.
        echo.
    )
)

REM Kiểm tra Docker
echo [Step 2] Kiểm tra Docker...
docker --version >nul 2>&1
if %errorlevel% neq 0 (
    echo.
    echo WARNING: Docker chưa được cài đặt hoặc chưa chạy!
    echo.
    echo Vui lòng:
    echo   1. Cài Docker Desktop từ: https://www.docker.com/products/docker-desktop
    echo   2. Mở Docker Desktop và đợi nó khởi động xong
    echo   3. Chạy lại script này
    echo.
    pause
    exit /b 1
)

docker ps >nul 2>&1
if %errorlevel% neq 0 (
    echo.
    echo WARNING: Docker daemon không chạy!
    echo.
    echo Vui lòng:
    echo   1. Mở Docker Desktop
    echo   2. Đợi Docker khởi động xong
    echo   3. Chạy lại script này
    echo.
    pause
    exit /b 1
)

echo Docker đang chạy!
echo.

REM Kiểm tra MySQL container
echo [Step 3] Kiểm tra MySQL container...
docker ps -a | findstr "rtp-mysql" >nul
if %errorlevel% neq 0 (
    echo MySQL container chưa tồn tại!
    echo.
    echo Đang tạo MySQL container...
    cd database
    call setup-docker-mysql.bat
    cd ..
    echo.
) else (
    echo MySQL container đã tồn tại!
    echo.
    
    REM Kiểm tra container có đang chạy không
    docker ps | findstr "rtp-mysql" >nul
    if %errorlevel% neq 0 (
        echo Container không đang chạy. Đang khởi động...
        docker start rtp-mysql
        echo.
        echo Đợi MySQL khởi động...
        timeout /t 10 >nul
        
        REM Kiểm tra MySQL đã sẵn sàng chưa
        :wait_mysql
        docker exec rtp-mysql mysqladmin ping -h localhost --silent >nul 2>&1
        if %errorlevel% neq 0 (
            echo Đang đợi MySQL...
            timeout /t 3 >nul
            goto :wait_mysql
        )
        echo MySQL đã sẵn sàng!
        echo.
    ) else (
        echo Container đang chạy!
        echo.
    )
)

REM Kiểm tra Java
echo [Step 4] Kiểm tra Java...
java -version >nul 2>&1
if %errorlevel% neq 0 (
    echo.
    echo WARNING: Java chưa được cài đặt hoặc chưa có trong PATH!
    echo.
    echo Vui lòng cài Java 21 và thêm vào PATH.
    echo.
) else (
    echo Java đã được cài đặt!
    java -version
    echo.
)

REM Kiểm tra Maven
echo [Step 5] Kiểm tra Maven...
mvn --version >nul 2>&1
if %errorlevel% neq 0 (
    echo.
    echo WARNING: Maven chưa được cài đặt hoặc chưa có trong PATH!
    echo.
    echo Vui lòng cài Maven và thêm vào PATH.
    echo.
) else (
    echo Maven đã được cài đặt!
    mvn --version | findstr "Apache Maven"
    echo.
)

REM Hỏi có muốn rebuild không
echo [Step 6] Rebuild project?
echo.
set /p REBUILD="Bạn có muốn rebuild project không? (Y/N): "
if /i "!REBUILD!"=="Y" (
    echo.
    echo Đang rebuild project...
    call REBUILD-ALL.bat
    echo.
) else (
    echo Bỏ qua rebuild.
    echo.
)

echo ========================================
echo Setup hoàn tất!
echo ========================================
echo.
echo Bạn có thể:
echo   1. Chạy server: START-SERVER.bat
echo   2. Chạy client: START-CLIENT-1.bat (hoặc các client khác)
echo.
echo Database đã sẵn sàng:
echo   - Host: localhost
echo   - Port: 3306
echo   - Database: rtp_conference
echo   - User: root
echo   - Password: (empty)
echo.
echo Default login:
echo   - Username: admin
echo   - Password: admin123
echo.
pause




