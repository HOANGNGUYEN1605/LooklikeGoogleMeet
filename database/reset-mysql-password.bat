@echo off
chcp 65001 >nul
echo ========================================
echo Reset MySQL Root Password
echo ========================================
echo.
echo Script này sẽ reset password của root user trong container
echo để cho phép kết nối không cần password.
echo.

REM Kiểm tra container có đang chạy không
docker ps | findstr "rtp-mysql" >nul
if %errorlevel% neq 0 (
    echo ERROR: Container rtp-mysql không đang chạy!
    echo Vui lòng chạy: docker start rtp-mysql
    pause
    exit /b 1
)

echo Container đang chạy. Đang reset password...
echo.

REM Tạo file SQL để reset password
echo ALTER USER 'root'@'localhost' IDENTIFIED BY ''; > reset-password.sql
echo ALTER USER 'root'@'%' IDENTIFIED BY ''; >> reset-password.sql
echo FLUSH PRIVILEGES; >> reset-password.sql

REM Copy file vào container và chạy
docker cp reset-password.sql rtp-mysql:/tmp/reset-password.sql
docker exec rtp-mysql sh -c "mysql -u root --password='' < /tmp/reset-password.sql 2>&1 || mysql -u root < /tmp/reset-password.sql 2>&1"

REM Xóa file tạm
del reset-password.sql 2>nul

echo.
echo Password reset completed!
echo.
pause




