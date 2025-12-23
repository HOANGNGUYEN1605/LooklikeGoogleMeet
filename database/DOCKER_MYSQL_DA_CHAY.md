# ✅ MySQL với Docker đã được setup thành công!

## Trạng thái hiện tại

- ✅ **Docker Desktop**: Đang chạy
- ✅ **MySQL Container**: `rtp-mysql` đang chạy
- ✅ **Database**: `rtp_conference` đã được tạo
- ✅ **User mặc định**: `admin` / `admin123` đã sẵn sàng

---

## Thông tin kết nối

- **Host**: `localhost`
- **Port**: `3306`
- **Database**: `rtp_conference`
- **User**: `root`
- **Password**: (trống - không có password)

**User để đăng nhập vào app:**
- **Username**: `admin`
- **Password**: `admin123`

---

## Các lệnh hữu ích

### Xem container đang chạy
```bash
docker ps
```

### Xem logs MySQL
```bash
docker logs rtp-mysql
```

### Dừng MySQL
```bash
docker stop rtp-mysql
```

### Khởi động lại MySQL
```bash
docker start rtp-mysql
```

### Kết nối MySQL bằng command line
```bash
docker exec -it rtp-mysql mysql -u root rtp_conference
```

### Xem danh sách users
```bash
docker exec rtp-mysql mysql -u root rtp_conference -e "SELECT * FROM users;"
```

### Xóa container (nếu cần reset)
```bash
docker stop rtp-mysql
docker rm rtp-mysql
```

Sau đó chạy lại `setup-docker-mysql.bat` để tạo lại.

---

## Bước tiếp theo

1. **Chạy server:**
   ```bash
   cd ..
   mvn clean install
   cd rtp-av-server
   mvn exec:java
   ```

2. **Chạy client:**
   ```bash
   cd rtp-av-client
   mvn exec:java
   ```

3. **Đăng nhập:**
   - Username: `admin`
   - Password: `admin123`
   - Hoặc đăng ký user mới!

---

## Lưu ý

- MySQL container sẽ tự động chạy khi Docker Desktop khởi động
- Nếu restart máy, chỉ cần đảm bảo Docker Desktop đang chạy
- Database được lưu trong Docker volume, không mất dữ liệu khi restart container
- Port 3306 đã được map ra ngoài, server có thể kết nối bình thường

---

## Troubleshooting

### Container không chạy
```bash
docker start rtp-mysql
```

### Kiểm tra MySQL đã sẵn sàng chưa
```bash
docker exec rtp-mysql mysqladmin ping -h localhost
```

Nếu thấy `mysqld is alive` → MySQL đã sẵn sàng!

### Reset database (xóa tất cả dữ liệu)
```bash
docker stop rtp-mysql
docker rm rtp-mysql
cd database
setup-docker-mysql.bat
```


