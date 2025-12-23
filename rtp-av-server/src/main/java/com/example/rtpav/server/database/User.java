package com.example.rtpav.server.database;

import java.sql.Timestamp;

/**
 * Entity class cho bảng users
 */
public class User {
    private int id;
    private String username;
    private String password; // Hashed password
    private String displayName;
    private String email;
    private Timestamp createdAt;
    
    public User(int id, String username, String password, String displayName, String email, Timestamp createdAt) {
        this.id = id;
        this.username = username;
        this.password = password;
        this.displayName = displayName;
        this.email = email;
        this.createdAt = createdAt;
    }
    
    public int getId() {
        return id;
    }
    
    public void setId(int id) {
        this.id = id;
    }
    
    public String getUsername() {
        return username;
    }
    
    public void setUsername(String username) {
        this.username = username;
    }
    
    public String getPassword() {
        return password;
    }
    
    public void setPassword(String password) {
        this.password = password;
    }
    
    public String getDisplayName() {
        return displayName;
    }
    
    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }
    
    public String getEmail() {
        return email;
    }
    
    public void setEmail(String email) {
        this.email = email;
    }
    
    public Timestamp getCreatedAt() {
        return createdAt;
    }
    
    public void setCreatedAt(Timestamp createdAt) {
        this.createdAt = createdAt;
    }
    
    @Override
    public String toString() {
        return "User{id=" + id + ", username='" + username + "', displayName='" + displayName + "'}";
    }
}




