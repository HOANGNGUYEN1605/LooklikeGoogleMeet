# RTP AV Conference - Hướng Dẫn Setup

## 📋 Giới Thiệu

Project này là một hệ thống video conference sử dụng RTP (Real-time Transport Protocol) và RMI (Remote Method Invocation) để kết nối nhiều clients với nhau thông qua server.

## ✅ Yêu Cầu Hệ Thống

- **Java 21** (bắt buộc)
- **Maven 3.6+**
- **Windows/Linux/macOS**
- **ZeroTier** (để kết nối từ xa, không cùng mạng LAN)

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

### **Bước 4: Build Project**

```bash
cd rtp-av-conference
mvn clean install
```

## 🎯 Chạy Project

### **Chạy Server:**

**Windows:**
```cmd
START-SERVER.bat
```

**Linux/macOS:**
```bash
./START-SERVER.sh
```

### **Chạy Client:**

**Windows:**
```cmd
START-CLIENT.bat
```

**Linux/macOS:**
```bash
./START-CLIENT.sh
```

**Với tham số:**
```cmd
START-CLIENT-VM.bat <SERVER_IP> <CLIENT_NAME> <ROOM_NAME>
```

## 📖 Hướng Dẫn Chi Tiết

- **Kết nối từ xa:** Xem [HUONG_DAN_CLIENT_TU_XA.md](HUONG_DAN_CLIENT_TU_XA.md)
- **Chạy nhiều clients:** Xem [HUONG_DAN_CHAY_NHIEU_CLIENT.md](HUONG_DAN_CHAY_NHIEU_CLIENT.md)
- **Tóm tắt nhanh:** Xem [HUONG_DAN_NHANH_CLIENT_TU_XA.txt](HUONG_DAN_NHANH_CLIENT_TU_XA.txt)

## 🔧 Troubleshooting

Xem file [FIX_IDE_ERRORS.md](FIX_IDE_ERRORS.md) nếu gặp lỗi IDE.

## 📞 Hỗ Trợ

Nếu gặp vấn đề, kiểm tra:
1. Java version phải là 21
2. Maven đã cài đúng chưa
3. ZeroTier đã join network chưa
4. Firewall có block ports không



