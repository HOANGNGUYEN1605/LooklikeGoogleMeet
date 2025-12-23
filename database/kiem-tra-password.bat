@echo off
chcp 65001 >nul
echo ========================================
echo KIỂM TRA PASSWORD HASH
echo ========================================
echo.
echo Username: hoang
echo Password: 123123
echo.
echo Hash trong database:
docker exec rtp-mysql mysql -u root rtp_conference -e "SELECT username, password FROM users WHERE username = 'hoang';"
echo.
echo.
echo Để test hash password, chạy:
echo   database\test-hash.bat
echo.
pause

