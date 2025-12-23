package com.example.rtpav.server.auth;

import com.example.rtpav.server.database.User;
import com.example.rtpav.server.database.UserDAO;

import java.sql.SQLException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * Service để xử lý authentication (đăng nhập/đăng ký)
 * Sử dụng SHA-256 để hash password (đơn giản, có thể nâng cấp lên BCrypt sau)
 */
public class AuthService {
    private final UserDAO userDAO;
    
    public AuthService() {
        this.userDAO = new UserDAO();
    }
    
    /**
     * Đăng nhập - kiểm tra username và password
     * @return User nếu đăng nhập thành công, null nếu thất bại
     */
    public User login(String username, String password) {
        try {
            if (username == null || username.trim().isEmpty()) {
                System.out.println("[AUTH] Login failed: Username is empty");
                return null;
            }
            if (password == null || password.isEmpty()) {
                System.out.println("[AUTH] Login failed: Password is empty for - " + username);
                return null;
            }
            
            username = username.trim();
            System.out.println("[AUTH] Login attempt for username: '" + username + "'");
            
            User user = userDAO.findByUsername(username);
            if (user == null) {
                System.out.println("[AUTH] Login failed: User not found - '" + username + "'");
                System.out.println("[AUTH] Hint: Username does not exist. Please check your username or register first.");
                return null;
            }
            
            System.out.println("[AUTH] User found: ID=" + user.getId() + ", Username='" + user.getUsername() + 
                             "', DisplayName='" + user.getDisplayName() + "'");
            
            // Hash password đầu vào và so sánh
            System.out.println("[AUTH] Hashing password for login...");
            System.out.println("[AUTH] Password length: " + (password != null ? password.length() : 0));
            
            String hashedPassword = hashPassword(password);
            String storedHash = user.getPassword();
            
            System.out.println("[AUTH] Password comparison:");
            System.out.println("[AUTH] Input hash (full): " + hashedPassword);
            System.out.println("[AUTH] Stored hash (full): " + (storedHash != null ? storedHash : "null"));
            System.out.println("[AUTH] Hash lengths - Input: " + hashedPassword.length() + ", Stored: " + (storedHash != null ? storedHash.length() : 0));
            System.out.println("[AUTH] Hashes match: " + hashedPassword.equals(storedHash));
            
            // Debug: Nếu không khớp, thử hash lại để xem
            if (!hashedPassword.equals(storedHash)) {
                System.err.println("[AUTH] WARNING: Password hashes do not match!");
                System.err.println("[AUTH] Re-hashing password to verify...");
                String rehashed = hashPassword(password);
                System.err.println("[AUTH] Re-hashed: " + rehashed);
                System.err.println("[AUTH] Re-hash matches input hash: " + rehashed.equals(hashedPassword));
                System.err.println("[AUTH] Re-hash matches stored hash: " + rehashed.equals(storedHash));
            }
            
            if (hashedPassword.equals(storedHash)) {
                System.out.println("[AUTH] Login successful: " + username + " (Display: " + user.getDisplayName() + ")");
                return user;
            } else {
                System.out.println("[AUTH] Login failed: Invalid password for - '" + username + "'");
                System.out.println("[AUTH] Hint: Password does not match. Please check your password.");
                return null;
            }
        } catch (SQLException e) {
            System.err.println("[AUTH] Database error during login: " + e.getMessage());
            e.printStackTrace();
            return null;
        } catch (Exception e) {
            System.err.println("[AUTH] Unexpected error during login: " + e.getMessage());
            e.printStackTrace();
            return null;
        }
    }
    
    /**
     * Đăng ký user mới
     * @return User nếu đăng ký thành công, null nếu thất bại
     */
    public User register(String username, String password, String displayName, String email) {
        try {
            // Validate input trước
            if (username == null || username.trim().isEmpty()) {
                System.out.println("[AUTH] Registration failed: Username is required");
                return null;
            }
            username = username.trim();
            
            // Validate username format
            if (!isValidUsername(username)) {
                System.out.println("[AUTH] Registration failed: Username must be 3-20 characters, only letters, numbers, and underscore");
                return null;
            }
            
            if (password == null || password.isEmpty()) {
                System.out.println("[AUTH] Registration failed: Password is required");
                return null;
            }
            
            // Validate password
            if (!isValidPassword(password)) {
                System.out.println("[AUTH] Registration failed: Password must be at least 6 characters");
                return null;
            }
            
            if (displayName == null || displayName.trim().isEmpty()) {
                displayName = username; // Dùng username làm display name nếu không có
            }
            displayName = displayName.trim();
            
            // Kiểm tra username đã tồn tại chưa (exact match, case-sensitive)
            System.out.println("[AUTH] Checking if username exists: '" + username + "'");
            
            // Double check với usernameExists trước
            boolean exists = userDAO.usernameExists(username);
            if (exists) {
                System.out.println("[AUTH] Registration failed: Username already exists (usernameExists check) - '" + username + "'");
                // Get existing user details for better error message
                User existingUser = userDAO.findByUsername(username);
                if (existingUser != null) {
                    System.out.println("[AUTH] Existing user details - Username: '" + existingUser.getUsername() + 
                                     "', Display Name: '" + existingUser.getDisplayName() + 
                                     "', Created: " + existingUser.getCreatedAt());
                }
                System.out.println("[AUTH] Hint: User '" + username + "' was registered before. Try logging in instead.");
                return null;
            }
            
            // Double check với findByUsername để đảm bảo
            User existingUser = userDAO.findByUsername(username);
            if (existingUser != null) {
                System.out.println("[AUTH] Registration failed: Username already exists (findByUsername check) - '" + username + "'");
                System.out.println("[AUTH] Existing user found - Username: '" + existingUser.getUsername() + 
                                 "', Display Name: '" + existingUser.getDisplayName() + 
                                 "', Created: " + existingUser.getCreatedAt());
                System.out.println("[AUTH] Hint: User '" + username + "' was registered before. Try logging in instead.");
                return null;
            }
            
            System.out.println("[AUTH] Username '" + username + "' is available for registration");
            
            // Hash password
            System.out.println("[AUTH] Hashing password for registration...");
            System.out.println("[AUTH] Password length: " + (password != null ? password.length() : 0));
            String hashedPassword = hashPassword(password);
            System.out.println("[AUTH] Registering user: " + username);
            System.out.println("[AUTH] Password hash (full): " + hashedPassword);
            
            // Tạo user mới
            System.out.println("[AUTH] Creating user: username='" + username + "', displayName='" + displayName + "'");
            boolean success = userDAO.createUser(username, hashedPassword, displayName, email != null ? email.trim() : "");
            
            if (success) {
                // Đợi một chút để đảm bảo database đã commit (nếu cần)
                Thread.sleep(50);
                
                // Verify user was created
                User createdUser = userDAO.findByUsername(username);
                if (createdUser != null) {
                    System.out.println("[AUTH] Registration successful: " + username + " (ID: " + createdUser.getId() + 
                                     ", Display: " + createdUser.getDisplayName() + ")");
                    return createdUser;
                } else {
                    System.err.println("[AUTH] ERROR: Registration created user but could not retrieve it: " + username);
                    System.err.println("[AUTH] This might indicate a database transaction issue");
                    return null;
                }
            } else {
                System.err.println("[AUTH] Registration failed: Could not create user - " + username);
                // Check if user somehow exists now (race condition?)
                if (userDAO.usernameExists(username)) {
                    System.err.println("[AUTH] WARNING: User '" + username + "' exists after failed creation (possible race condition)");
                }
                return null;
            }
        } catch (SQLException e) {
            System.err.println("[AUTH] Database error during registration: " + e.getMessage());
            System.err.println("[AUTH] SQL State: " + e.getSQLState() + ", Error Code: " + e.getErrorCode());
            e.printStackTrace();
            return null;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            System.err.println("[AUTH] Interrupted during registration");
            return null;
        } catch (Exception e) {
            System.err.println("[AUTH] Unexpected error during registration: " + e.getMessage());
            e.printStackTrace();
            return null;
        }
    }
    
    /**
     * Hash password bằng SHA-256 (đơn giản, có thể nâng cấp lên BCrypt)
     */
    private String hashPassword(String password) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            // Sử dụng UTF-8 encoding để đảm bảo consistency
            byte[] hash = md.digest(password.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) {
                    hexString.append('0');
                }
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (NoSuchAlgorithmException e) {
            System.err.println("[AUTH] Error hashing password: " + e.getMessage());
            e.printStackTrace();
            throw new RuntimeException("Failed to hash password", e);
        }
    }
    
    /**
     * Validate username
     */
    private boolean isValidUsername(String username) {
        if (username == null || username.trim().isEmpty()) {
            return false;
        }
        username = username.trim();
        // Username: 3-20 ký tự, chỉ chữ cái, số, dấu gạch dưới
        return username.length() >= 3 && username.length() <= 20 && 
               username.matches("^[a-zA-Z0-9_]+$");
    }
    
    /**
     * Validate password
     */
    private boolean isValidPassword(String password) {
        if (password == null) {
            return false;
        }
        // Password: ít nhất 6 ký tự
        return password.length() >= 6;
    }
    
    /**
     * Reset password cho user
     * @return true nếu thành công, false nếu thất bại
     */
    public boolean resetPassword(String username, String newPassword) {
        try {
            if (username == null || username.trim().isEmpty()) {
                System.out.println("[AUTH] Reset password failed: Username is required");
                return false;
            }
            username = username.trim();
            
            if (newPassword == null || newPassword.isEmpty()) {
                System.out.println("[AUTH] Reset password failed: New password is required");
                return false;
            }
            
            if (!isValidPassword(newPassword)) {
                System.out.println("[AUTH] Reset password failed: Password must be at least 6 characters");
                return false;
            }
            
            // Kiểm tra user có tồn tại không
            User user = userDAO.findByUsername(username);
            if (user == null) {
                System.out.println("[AUTH] Reset password failed: User not found - " + username);
                return false;
            }
            
            // Hash password mới
            String hashedPassword = hashPassword(newPassword);
            
            // Cập nhật password
            boolean success = userDAO.updatePassword(username, hashedPassword);
            if (success) {
                System.out.println("[AUTH] Password reset successful for: " + username);
                return true;
            } else {
                System.err.println("[AUTH] Reset password failed: Could not update password for - " + username);
                return false;
            }
        } catch (SQLException e) {
            System.err.println("[AUTH] Database error during password reset: " + e.getMessage());
            e.printStackTrace();
            return false;
        } catch (Exception e) {
            System.err.println("[AUTH] Unexpected error during password reset: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }
    
    /**
     * Kiểm tra kết nối database
     */
    public boolean isDatabaseAvailable() {
        try {
            return com.example.rtpav.server.database.DatabaseConnection.getInstance().testConnection();
        } catch (Exception e) {
            return false;
        }
    }
}



