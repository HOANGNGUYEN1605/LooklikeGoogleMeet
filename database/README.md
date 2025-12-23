# Database Setup

## Yêu cầu
- MySQL 8.0 hoặc cao hơn
- Quyền tạo database và user

## Cài đặt

1. **Tạo database và bảng:**
   ```bash
   mysql -u root -p < schema.sql
   ```

2. **Cấu hình kết nối:**
   
   Mặc định, ứng dụng sử dụng:
   - URL: `jdbc:mysql://localhost:3306/rtp_conference`
   - User: `root`
   - Password: (trống)
   
   Để thay đổi, sử dụng system properties khi chạy server:
   ```bash
   java -Ddb.url=jdbc:mysql://localhost:3306/rtp_conference \
        -Ddb.user=your_username \
        -Ddb.password=your_password \
        -jar rtp-av-server.jar
   ```

## User mặc định

Sau khi chạy `schema.sql`, có một user mặc định:
- Username: `admin`
- Password: `admin123` (đã được hash bằng SHA-256)

**LƯU Ý:** Để bảo mật tốt hơn, nên đổi password sau lần đăng nhập đầu tiên.

## Cấu trúc bảng

### users
- `id`: INT AUTO_INCREMENT PRIMARY KEY
- `username`: VARCHAR(50) UNIQUE - Tên đăng nhập
- `password`: VARCHAR(255) - Mật khẩu đã hash (SHA-256)
- `display_name`: VARCHAR(100) - Tên hiển thị
- `email`: VARCHAR(100) - Email (tùy chọn)
- `created_at`: TIMESTAMP - Thời gian tạo
- `updated_at`: TIMESTAMP - Thời gian cập nhật




