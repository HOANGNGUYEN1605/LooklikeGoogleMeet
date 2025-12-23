-- Script để sửa collation của cột username thành case-sensitive
-- Chạy: docker exec -i rtp-mysql mysql -u root rtp_conference < sua-collation-username.sql

USE rtp_conference;

-- Sửa collation của cột username thành utf8mb4_bin (case-sensitive)
ALTER TABLE users MODIFY COLUMN username VARCHAR(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL;

-- Kiểm tra lại collation
SHOW FULL COLUMNS FROM users WHERE Field='username';

