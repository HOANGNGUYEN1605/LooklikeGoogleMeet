package com.example.rtpav.server.database;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

/**
 * Quản lý kết nối đến MySQL database
 */
public class DatabaseConnection {
    private static DatabaseConnection instance;
    private Connection connection;
    
    // Database configuration - có thể đọc từ file config hoặc environment variables
    private static final String DB_URL = System.getProperty("db.url", "jdbc:mysql://localhost:3306/rtp_conference?useSSL=false&serverTimezone=UTC&allowPublicKeyRetrieval=true");
    private static final String DB_USER = System.getProperty("db.user", "root");
    private static final String DB_PASSWORD = System.getProperty("db.password", "");
    
    private DatabaseConnection() {
        try {
            // Load MySQL driver
            Class.forName("com.mysql.cj.jdbc.Driver");
        } catch (ClassNotFoundException e) {
            System.err.println("[DATABASE] MySQL Driver not found: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    public static synchronized DatabaseConnection getInstance() {
        if (instance == null) {
            instance = new DatabaseConnection();
        }
        return instance;
    }
    
    /**
     * Lấy connection đến database
     */
    public Connection getConnection() throws SQLException {
        if (connection == null || connection.isClosed()) {
            try {
                connection = DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD);
                // Đảm bảo auto-commit được bật (mặc định MySQL là true)
                connection.setAutoCommit(true);
                System.out.println("[DATABASE] Connected to MySQL database: " + DB_URL);
            } catch (SQLException e) {
                System.err.println("[DATABASE] Failed to connect to database: " + e.getMessage());
                throw e;
            }
        }
        // Đảm bảo connection vẫn mở và auto-commit được bật
        try {
            if (connection.isClosed()) {
                connection = DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD);
                connection.setAutoCommit(true);
            } else {
                connection.setAutoCommit(true);
            }
        } catch (SQLException e) {
            System.err.println("[DATABASE] Error checking connection: " + e.getMessage());
        }
        return connection;
    }
    
    /**
     * Đóng connection
     */
    public void closeConnection() {
        if (connection != null) {
            try {
                connection.close();
                System.out.println("[DATABASE] Database connection closed");
            } catch (SQLException e) {
                System.err.println("[DATABASE] Error closing connection: " + e.getMessage());
            }
        }
    }
    
    /**
     * Test connection
     */
    public boolean testConnection() {
        try {
            Connection conn = getConnection();
            return conn != null && !conn.isClosed();
        } catch (SQLException e) {
            System.err.println("[DATABASE] Connection test failed: " + e.getMessage());
            return false;
        }
    }
}



