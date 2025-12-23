@echo off
chcp 65001 >nul
echo ========================================
echo SỬA COLLATION CỦA CỘT USERNAME
echo ========================================
echo.
echo Script này sẽ sửa collation của cột username từ utf8mb4_unicode_ci (case-insensitive)
echo thành utf8mb4_bin (case-sensitive) để đảm bảo so sánh username chính xác.
echo.
echo CẢNH BÁO: Sau khi sửa, "Admin" và "admin" sẽ được coi là khác nhau!
echo.
pause

echo.
echo Đang sửa collation...
docker exec -i rtp-mysql mysql -u root rtp_conference < database\sua-collation-username.sql

if %ERRORLEVEL% EQU 0 (
    echo.
    echo ========================================
    echo Đã sửa collation thành công!
    echo ========================================
    echo.
    echo Kiểm tra collation mới:
    docker exec rtp-mysql mysql -u root rtp_conference -e "SHOW FULL COLUMNS FROM users WHERE Field='username';"
) else (
    echo.
    echo ========================================
    echo CÓ LỖI XẢY RA!
    echo ========================================
)

echo.
pause

