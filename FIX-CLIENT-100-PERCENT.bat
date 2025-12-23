@echo off
title Fix Client - 100% Success
color 0C
cls
echo.
echo ========================================
echo   FIX CLIENT - ĐẢM BẢO 100% THÀNH CÔNG
echo ========================================
echo.
cd /d "%~dp0"

echo [BƯỚC 1] Xóa thư mục target cũ...
if exist "rtp-av-client\target" (
    echo   - Xóa rtp-av-client\target...
    rmdir /s /q "rtp-av-client\target" 2>nul
    if exist "rtp-av-client\target" (
        echo   [WARNING] Không thể xóa rtp-av-client\target (có thể đang được sử dụng)
    ) else (
        echo   [OK] Đã xóa rtp-av-client\target
    )
) else (
    echo   [OK] rtp-av-client\target không tồn tại
)

if exist "rtp-av-server\target" (
    echo   - Xóa rtp-av-server\target...
    rmdir /s /q "rtp-av-server\target" 2>nul
    echo   [OK] Đã xóa rtp-av-server\target
)

if exist "rtp-rmi-common\target" (
    echo   - Xóa rtp-rmi-common\target...
    rmdir /s /q "rtp-rmi-common\target" 2>nul
    echo   [OK] Đã xóa rtp-rmi-common\target
)

echo.
echo [BƯỚC 2] Kiểm tra Java...
set JAVA_HOME=C:\Program Files\Java\jdk-21
if not exist "%JAVA_HOME%\bin\java.exe" (
    echo   [ERROR] Java 21 not found at %JAVA_HOME%
    echo   Vui lòng sửa JAVA_HOME trong script này!
    pause
    exit /b 1
)
echo   [OK] Java found: %JAVA_HOME%
set PATH=%JAVA_HOME%\bin;%PATH%

echo.
echo [BƯỚC 3] Kiểm tra Maven...
set MAVEN_PATH=D:\java_mvc\apache-maven-3.9.12\bin\mvn.cmd
if not exist "%MAVEN_PATH%" (
    echo   [ERROR] Maven not found at %MAVEN_PATH%
    echo   Vui lòng sửa MAVEN_PATH trong script này!
    pause
    exit /b 1
)
echo   [OK] Maven found: %MAVEN_PATH%

echo.
echo [BƯỚC 4] Rebuild project...
echo   Đang chạy: mvn clean install
echo   Vui lòng đợi (có thể mất 1-2 phút)...
echo.

call "%MAVEN_PATH%" clean install -DskipTests

if errorlevel 1 (
    echo.
    echo ========================================
    echo   [ERROR] BUILD FAILED!
    echo ========================================
    echo.
    echo Vui lòng kiểm tra lỗi ở trên và sửa lại!
    pause
    exit /b 1
)

echo.
echo ========================================
echo   [SUCCESS] BUILD THÀNH CÔNG!
echo ========================================
echo.
echo [BƯỚC 5] Kiểm tra file .class mới...
if exist "rtp-av-client\target\classes\com\example\rtpav\client\LoginDialog.class" (
    echo   [OK] LoginDialog.class đã được tạo mới
) else (
    echo   [WARNING] Không tìm thấy LoginDialog.class
)

echo.
echo ========================================
echo   HOÀN TẤT!
echo ========================================
echo.
echo Bây giờ bạn có thể chạy START-CLIENT-1.bat
echo.
echo LƯU Ý:
echo - Kiểm tra console log khi chạy client
echo - Phải thấy: [LOGIN] Using server IP: 172.24.23.39
echo - Nếu thấy IP khác → Chạy lại script này
echo.
pause

