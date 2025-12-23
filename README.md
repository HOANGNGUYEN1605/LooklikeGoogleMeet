# RTP AV Conference - Video Conference Application

## 📋 Giới Thiệu

Project này là một hệ thống video conference với giao diện giống Google Meet, sử dụng RTP (Real-time Transport Protocol) và RMI (Remote Method Invocation) để kết nối nhiều clients với nhau thông qua server.

## ✨ Tính Năng

- ✅ **Video & Audio Conference**: Hỗ trợ video và audio real-time giữa nhiều người dùng
- ✅ **Database Authentication**: Đăng nhập/đăng ký với MySQL database
- ✅ **Chat System**: 
  - Chat chung (public chat) cho tất cả người trong phòng
  - Chat riêng (private chat) giữa 2 người
  - Emoji picker để gửi emoji
  - Gửi file (tối đa 10MB)
- ✅ **Modern UI**: Giao diện đẹp, tối giản giống Google Meet
- ✅ **Mic & Camera Control**: Bật/tắt mic và camera dễ dàng

## ✅ Yêu Cầu Hệ Thống

- **Java 21** (bắt buộc)
- **Maven 3.6+**
- **Docker Desktop** (để chạy MySQL database)
- **Windows/Linux/macOS**
- **ZeroTier** (để kết nối từ xa, không cùng mạng LAN - tùy chọn)

## 🚀 Setup Nhanh

### **Bước 1: Cài Đặt Java 21**

**Windows:**
1. Tải từ: https://adoptium.net/
2. Chọn **Java 21 LTS**
3. Cài đặt và set JAVA_HOME

**Linux (Ubuntu/Debian):**
```bash
sudo apt update
sudo apt install openjdk-21-jdk
```

**macOS:**
```bash
brew install openjdk@21
```

**Kiểm tra:**
```bash
java -version  # Phải hiển thị version 21
```

### **Bước 2: Cài Đặt Maven**

**Windows:**
1. Tải từ: https://maven.apache.org/download.cgi
2. Giải nén vào thư mục (ví dụ: `C:\apache-maven-3.9.6`)
3. Thêm vào PATH: `C:\apache-maven-3.9.6\bin`

**Linux:**
```bash
sudo apt install maven
```

**macOS:**
```bash
brew install maven
```

**Kiểm tra:**
```bash
mvn --version
```

### **Bước 3: Cài Đặt ZeroTier (Cho Kết Nối Từ Xa)**

**Windows:**
1. Tải từ: https://www.zerotier.com/download/
2. Cài đặt và chạy ZeroTier
3. Join network với Network ID (sẽ được cung cấp)

**Linux:**
```bash
curl -s https://install.zerotier.com | sudo bash
sudo zerotier-cli join <NETWORK_ID>
```

**macOS:**
1. Tải từ: https://www.zerotier.com/download/
2. Cài đặt và join network

### **Bước 4: Setup Database với Docker**

**Windows:**
```cmd
cd database
setup-docker-mysql.bat
```

Script này sẽ:
- Tạo MySQL container trong Docker
- Setup database `rtp_conference`
- Tạo user mặc định: `admin` / `admin123`

**Lưu ý:** Đảm bảo Docker Desktop đang chạy trước khi chạy script.

### **Bước 5: Build Project**

```bash
cd rtp-av-conference
mvn clean install
```

## 🎯 Chạy Project

### **Bước 1: Đảm bảo Database đang chạy**

Kiểm tra MySQL container:
```cmd
docker ps
```

Nếu container không chạy:
```cmd
cd database
docker start rtp-mysql
```

Hoặc tạo lại container:
```cmd
cd database
recreate-mysql-container.bat
```

### **Bước 2: Chạy Server**

**Windows:**
```cmd
START-SERVER.bat
```

**Linux/macOS:**
```bash
./START-SERVER.sh
```

Đợi đến khi thấy dòng "RMI ready" trong console.

### **Bước 3: Chạy Client**

**Windows - Chạy từng client:**
```cmd
START-CLIENT-1.bat  (Alice - Port 6000)
START-CLIENT-2.bat  (Bob - Port 6001)
START-CLIENT-3.bat  (Charlie - Port 6002)
START-CLIENT-4.bat  (David - Port 6003)
START-CLIENT-5.bat  (Eve - Port 6004)
START-CLIENT-6.bat  (Frank - Port 6005)
```

**Windows - Chạy tất cả 6 client cùng lúc:**
```cmd
START-ALL-6-CLIENTS.bat
```

### **Bước 4: Đăng nhập**

- **Username mặc định:** `admin`
- **Password mặc định:** `admin123`
- Hoặc đăng ký tài khoản mới trong ứng dụng

**Lưu ý:**
- Server phải chạy TRƯỚC khi chạy client
- Database phải đang chạy (Docker container `rtp-mysql`)
- Mỗi client sẽ mở một cửa sổ riêng
- Tất cả client kết nối đến server tại `localhost` và room `demo`
- Để chạy client trên máy khác, cần chỉnh `--server localhost` thành IP của máy chạy server

## 📖 Hướng Dẫn Chi Tiết

- **Kết nối từ xa:** Xem [HUONG_DAN_CLIENT_TU_XA.md](HUONG_DAN_CLIENT_TU_XA.md)
- **Chạy nhiều clients:** Xem [HUONG_DAN_CHAY_NHIEU_CLIENT.md](HUONG_DAN_CHAY_NHIEU_CLIENT.md)
- **Tóm tắt nhanh:** Xem [HUONG_DAN_NHANH_CLIENT_TU_XA.txt](HUONG_DAN_NHANH_CLIENT_TU_XA.txt)

## 🔧 Troubleshooting

### Database không kết nối được

Nếu gặp lỗi kết nối database sau khi tạo lại Docker container:
```cmd
cd database
fix-database-connection.bat
```

Hoặc tạo lại container:
```cmd
cd database
recreate-mysql-container.bat
```

### Mic không tắt được

Đã được fix trong version mới nhất. Nếu vẫn gặp vấn đề, hãy rebuild project:
```cmd
REBUILD-ALL.bat
```

### File không tải được

- Kiểm tra console log để xem fileId
- Đảm bảo file không quá 10MB
- Thử gửi lại file

## 📞 Hỗ Trợ

Nếu gặp vấn đề, kiểm tra:
1. Java version phải là 21
2. Maven đã cài đúng chưa
3. Docker Desktop đang chạy và MySQL container đang chạy
4. ZeroTier đã join network chưa (nếu kết nối từ xa)
5. Firewall có block ports không

## 📝 Changelog

### Version mới nhất:
- ✅ Thêm database authentication (MySQL với Docker)
- ✅ Cải thiện giao diện chat (màu sắc, contrast)
- ✅ Thêm emoji picker
- ✅ Thêm tính năng gửi file (tối đa 10MB)
- ✅ Fix lỗi mic không tắt đúng
- ✅ Cải thiện tab switching trong chat



