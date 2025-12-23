@echo off
chcp 65001 >nul
echo ========================================
echo XÓA TẤT CẢ USERS TEST (GIỮ LẠI ADMIN)
echo ========================================
echo.
echo CẢNH BÁO: Script này sẽ xóa TẤT CẢ users trừ 'admin'
echo.
pause

echo.
echo Đang xóa users...
docker exec rtp-mysql mysql -u root rtp_conference -e "DELETE FROM users WHERE username != 'admin';"

if %ERRORLEVEL% EQU 0 (
    echo.
    echo ========================================
    echo Đã xóa thành công!
    echo ========================================
    echo.
    echo Danh sách users còn lại:
    docker exec rtp-mysql mysql -u root rtp_conference -e "SELECT id, username, display_name FROM users;"
) else (
    echo.
    echo ========================================
    echo CÓ LỖI XẢY RA!
    echo ========================================
)

echo.
pause

