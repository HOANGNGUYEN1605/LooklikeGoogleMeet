package com.example.rtpav.server.database;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

/**
 * Data Access Object cho bảng users
 */
public class UserDAO {
    private final DatabaseConnection dbConnection;
    
    public UserDAO() {
        this.dbConnection = DatabaseConnection.getInstance();
    }
    
    /**
     * Tìm user theo username (case-sensitive, exact match)
     * Sử dụng BINARY để đảm bảo so sánh case-sensitive (vì collation utf8mb4_unicode_ci là case-insensitive)
     */
    public User findByUsername(String username) throws SQLException {
        if (username == null) return null;
        username = username.trim();
        // Sử dụng BINARY để so sánh case-sensitive (vì collation utf8mb4_unicode_ci là case-insensitive)
        String sql = "SELECT id, username, password, display_name, email, created_at FROM users WHERE BINARY username = ?";
        try (Connection conn = dbConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, username);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    User user = new User(
                        rs.getInt("id"),
                        rs.getString("username"),
                        rs.getString("password"),
                        rs.getString("display_name"),
                        rs.getString("email"),
                        rs.getTimestamp("created_at")
                    );
                    System.out.println("[DAO] findByUsername: Found user - ID=" + user.getId() + 
                                     ", username='" + user.getUsername() + "'");
                    return user;
                }
            }
        }
        System.out.println("[DAO] findByUsername: User not found - '" + username + "'");
        return null;
    }
    
    /**
     * Tìm user theo ID
     */
    public User findById(int id) throws SQLException {
        String sql = "SELECT id, username, password, display_name, email, created_at FROM users WHERE id = ?";
        try (Connection conn = dbConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, id);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return new User(
                        rs.getInt("id"),
                        rs.getString("username"),
                        rs.getString("password"),
                        rs.getString("display_name"),
                        rs.getString("email"),
                        rs.getTimestamp("created_at")
                    );
                }
            }
        }
        return null;
    }
    
    /**
     * Tạo user mới
     */
    public boolean createUser(String username, String password, String displayName, String email) throws SQLException {
        if (username == null || username.trim().isEmpty()) {
            System.err.println("[DAO] Cannot create user: username is null or empty");
            return false;
        }
        username = username.trim();
        
        String sql = "INSERT INTO users (username, password, display_name, email) VALUES (?, ?, ?, ?)";
        try (Connection conn = dbConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            // Đảm bảo auto-commit được bật
            conn.setAutoCommit(true);
            
            stmt.setString(1, username);
            stmt.setString(2, password);
            stmt.setString(3, displayName != null ? displayName.trim() : username);
            stmt.setString(4, email != null ? email.trim() : "");
            
            System.out.println("[DAO] Attempting to create user: username='" + username + "', displayName='" + displayName + "'");
            
            int rowsAffected = stmt.executeUpdate();
            
            if (rowsAffected > 0) {
                System.out.println("[DAO] User created successfully: " + username + " (rows affected: " + rowsAffected + ")");
                // Verify the user was actually created
                User created = findByUsername(username);
                if (created != null) {
                    System.out.println("[DAO] Verified user exists in database: " + username + " (ID: " + created.getId() + ")");
                    return true;
                } else {
                    System.err.println("[DAO] WARNING: User created but cannot be found immediately: " + username);
                    return false;
                }
            } else {
                System.err.println("[DAO] Failed to create user: " + username + " (no rows affected)");
                return false;
            }
        } catch (SQLException e) {
            System.err.println("[DAO] SQL Error creating user '" + username + "': " + e.getMessage());
            System.err.println("[DAO] SQL State: " + e.getSQLState() + ", Error Code: " + e.getErrorCode());
            if (e.getErrorCode() == 1062) { // Duplicate entry
                System.err.println("[DAO] Username '" + username + "' already exists (duplicate key)");
            }
            e.printStackTrace();
            throw e;
        }
    }
    
    /**
     * Cập nhật password
     */
    public boolean updatePassword(String username, String newPassword) throws SQLException {
        String sql = "UPDATE users SET password = ? WHERE username = ?";
        try (Connection conn = dbConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, newPassword);
            stmt.setString(2, username);
            int rowsAffected = stmt.executeUpdate();
            return rowsAffected > 0;
        }
    }
    
    /**
     * Cập nhật display name
     */
    public boolean updateDisplayName(String username, String displayName) throws SQLException {
        String sql = "UPDATE users SET display_name = ? WHERE username = ?";
        try (Connection conn = dbConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, displayName);
            stmt.setString(2, username);
            int rowsAffected = stmt.executeUpdate();
            return rowsAffected > 0;
        }
    }
    
    /**
     * Kiểm tra username đã tồn tại chưa (case-sensitive, exact match)
     * Sử dụng BINARY để đảm bảo so sánh case-sensitive (vì collation utf8mb4_unicode_ci là case-insensitive)
     */
    public boolean usernameExists(String username) throws SQLException {
        if (username == null || username.trim().isEmpty()) {
            System.out.println("[DAO] usernameExists: username is null or empty");
            return false;
        }
        username = username.trim();
        
        // Sử dụng BINARY để so sánh case-sensitive (vì collation utf8mb4_unicode_ci là case-insensitive)
        String sql = "SELECT COUNT(*) FROM users WHERE BINARY username = ?";
        try (Connection conn = dbConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, username);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    int count = rs.getInt(1);
                    if (count > 0) {
                        System.out.println("[DAO] usernameExists: Username '" + username + "' EXISTS (count: " + count + ")");
                        // Log all matching usernames for debugging
                        User existing = findByUsername(username);
                        if (existing != null) {
                            System.out.println("[DAO] Existing user details: ID=" + existing.getId() + 
                                             ", username='" + existing.getUsername() + 
                                             "', displayName='" + existing.getDisplayName() + "'");
                        }
                    } else {
                        System.out.println("[DAO] usernameExists: Username '" + username + "' does NOT exist (case-sensitive check)");
                    }
                    return count > 0;
                }
            }
        }
        System.out.println("[DAO] usernameExists: Username '" + username + "' does NOT exist (no result)");
        return false;
    }
    
    /**
     * Lấy tất cả users (để quản lý)
     */
    public List<User> getAllUsers() throws SQLException {
        List<User> users = new ArrayList<>();
        String sql = "SELECT id, username, password, display_name, email, created_at FROM users ORDER BY created_at DESC";
        try (Connection conn = dbConnection.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                users.add(new User(
                    rs.getInt("id"),
                    rs.getString("username"),
                    rs.getString("password"),
                    rs.getString("display_name"),
                    rs.getString("email"),
                    rs.getTimestamp("created_at")
                ));
            }
        }
        return users;
    }
}



