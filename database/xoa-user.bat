@echo off
chcp 65001 >nul
REM Script để xóa một user cụ thể
REM Usage: xoa-user.bat <username>

if "%1"=="" (
    echo Usage: xoa-user.bat ^<username^>
    echo Example: xoa-user.bat testuser
    exit /b 1
)

set USERNAME=%1

echo ========================================
echo XÓA USER: %USERNAME%
echo ========================================
echo.
echo CẢNH BÁO: Bạn có chắc muốn xóa user '%USERNAME%'?
pause

echo.
echo Đang xóa user '%USERNAME%'...
docker exec rtp-mysql mysql -u root rtp_conference -e "DELETE FROM users WHERE username = '%USERNAME%';"

if %ERRORLEVEL% EQU 0 (
    echo.
    echo ========================================
    echo Đã xóa user '%USERNAME%' thành công!
    echo ========================================
    echo.
    echo Danh sách users còn lại:
    docker exec rtp-mysql mysql -u root rtp_conference -e "SELECT id, username, display_name FROM users ORDER BY id;"
) else (
    echo.
    echo ========================================
    echo CÓ LỖI XẢY RA!
    echo ========================================
)

echo.
pause

