@echo off
chcp 65001 >nul
echo ========================================
echo Danh sách Users với Password Hash
echo ========================================
echo.
echo LƯU Ý: Password được hash bằng SHA-256, không thể xem password gốc.
echo Chỉ có thể xem password hash để debug.
echo.

docker exec rtp-mysql mysql -u root rtp_conference --table -e "SELECT username, display_name, LEFT(password, 40) as password_hash, email, created_at FROM users ORDER BY created_at DESC;"

echo.
echo ========================================
echo Để xem password hash đầy đủ của user cụ thể:
echo   docker exec rtp-mysql mysql -u root rtp_conference -e "SELECT username, password FROM users WHERE username = 'ten_user';"
echo ========================================
echo.
pause


