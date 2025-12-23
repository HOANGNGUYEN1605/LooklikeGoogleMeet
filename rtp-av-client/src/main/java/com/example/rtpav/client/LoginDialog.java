package com.example.rtpav.client;

import com.example.rtpav.rmi.ConferenceService;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.rmi.registry.LocateRegistry;

public class LoginDialog extends JDialog {
    private static final Color BG_PRIMARY = new Color(18, 18, 18);
    private static final Color BG_SECONDARY = new Color(30, 30, 30);
    private static final Color ACCENT_BLUE = new Color(0, 122, 255);
    private static final Color ACCENT_BLUE_LIGHT = new Color(64, 156, 255);
    private static final Color TEXT_PRIMARY = new Color(255, 255, 255);
    private static final Color TEXT_SECONDARY = new Color(174, 174, 178);
    
    // Login panel fields
    private JTextField loginUsernameField;
    private JPasswordField loginPasswordField;
    private JTextField loginRoomField;
    private JTextField loginServerField;
    private JButton loginButton;
    
    // Register panel fields
    private JTextField registerUsernameField;
    private JPasswordField registerPasswordField;
    private JTextField registerDisplayNameField;
    private JTextField registerEmailField;
    private JTextField registerRoomField;
    private JTextField registerServerField;
    private JButton registerButton;
    
    private JTabbedPane tabbedPane;
    
    private boolean joined = false;
    private String displayName;
    private String room;
    private String server;
    private ConferenceService conferenceService;
    
    public LoginDialog(Frame parent, String defaultServer) {
        super(parent, "Đăng nhập", true);
        
        setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);
        getContentPane().setBackground(BG_PRIMARY);
        setLayout(new BorderLayout(20, 20));
        ((JComponent)getContentPane()).setBorder(new EmptyBorder(30, 40, 30, 40));
        
        // Title
        JLabel titleLabel = new JLabel("Video Conference");
        titleLabel.setFont(new Font("Segoe UI", Font.BOLD, 28));
        titleLabel.setForeground(TEXT_PRIMARY);
        titleLabel.setBorder(new EmptyBorder(0, 0, 20, 0));
        add(titleLabel, BorderLayout.NORTH);
        
        // Tabbed pane cho Login/Register
        tabbedPane = new JTabbedPane();
        tabbedPane.setBackground(BG_PRIMARY);
        tabbedPane.setForeground(TEXT_PRIMARY);
        tabbedPane.setBorder(new EmptyBorder(0, 0, 0, 0));
        
        // Login tab
        tabbedPane.addTab("Đăng nhập", createLoginPanel());
        // Register tab
        tabbedPane.addTab("Đăng ký", createRegisterPanel());
        
        add(tabbedPane, BorderLayout.CENTER);
        
        pack();
        setLocationRelativeTo(parent);
        setResizable(false);
    }
    
    private JPanel createLoginPanel() {
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setBackground(BG_PRIMARY);
        panel.setBorder(new EmptyBorder(20, 0, 20, 0));
        
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(10, 0, 10, 0);
        gbc.anchor = GridBagConstraints.WEST;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.weightx = 1.0;
        
        // Username field
        gbc.gridx = 0;
        gbc.gridy = 0;
        JLabel usernameLabel = new JLabel("Tên đăng nhập:");
        usernameLabel.setFont(new Font("Segoe UI", Font.PLAIN, 14));
        usernameLabel.setForeground(TEXT_PRIMARY);
        panel.add(usernameLabel, gbc);
        
        gbc.gridy = 1;
        loginUsernameField = new JTextField(25);
        styleTextField(loginUsernameField);
        panel.add(loginUsernameField, gbc);
        
        // Password field
        gbc.gridy = 2;
        JLabel passwordLabel = new JLabel("Mật khẩu:");
        passwordLabel.setFont(new Font("Segoe UI", Font.PLAIN, 14));
        passwordLabel.setForeground(TEXT_PRIMARY);
        panel.add(passwordLabel, gbc);
        
        gbc.gridy = 3;
        loginPasswordField = new JPasswordField(25);
        styleTextField(loginPasswordField);
        panel.add(loginPasswordField, gbc);
        
        // Room field
        gbc.gridy = 4;
        JLabel roomLabel = new JLabel("Phòng hội thoại:");
        roomLabel.setFont(new Font("Segoe UI", Font.PLAIN, 14));
        roomLabel.setForeground(TEXT_PRIMARY);
        panel.add(roomLabel, gbc);
        
        gbc.gridy = 5;
        loginRoomField = new JTextField("demo", 25);
        styleTextField(loginRoomField);
        panel.add(loginRoomField, gbc);
        
        // Server field
        gbc.gridy = 6;
        JLabel serverLabel = new JLabel("Server:");
        serverLabel.setFont(new Font("Segoe UI", Font.PLAIN, 14));
        serverLabel.setForeground(TEXT_SECONDARY);
        panel.add(serverLabel, gbc);
        
        gbc.gridy = 7;
        loginServerField = new JTextField("localhost", 25);
        styleTextField(loginServerField);
        panel.add(loginServerField, gbc);
        
        // Login button
        gbc.gridy = 8;
        gbc.insets = new Insets(20, 0, 0, 0);
        loginButton = createStyledButton("Đăng nhập", ACCENT_BLUE);
        loginButton.addActionListener(e -> performLogin());
        panel.add(loginButton, gbc);
        
        // Enter key listener
        KeyAdapter enterKeyListener = new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                if (e.getKeyCode() == KeyEvent.VK_ENTER) {
                    loginButton.doClick();
                }
            }
        };
        loginUsernameField.addKeyListener(enterKeyListener);
        loginPasswordField.addKeyListener(enterKeyListener);
        loginRoomField.addKeyListener(enterKeyListener);
        
        return panel;
    }
    
    private JPanel createRegisterPanel() {
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setBackground(BG_PRIMARY);
        panel.setBorder(new EmptyBorder(20, 0, 20, 0));
        
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(10, 0, 10, 0);
        gbc.anchor = GridBagConstraints.WEST;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.weightx = 1.0;
        
        // Username field
        gbc.gridx = 0;
        gbc.gridy = 0;
        JLabel usernameLabel = new JLabel("Tên đăng nhập:");
        usernameLabel.setFont(new Font("Segoe UI", Font.PLAIN, 14));
        usernameLabel.setForeground(TEXT_PRIMARY);
        panel.add(usernameLabel, gbc);
        
        gbc.gridy = 1;
        registerUsernameField = new JTextField(25);
        styleTextField(registerUsernameField);
        panel.add(registerUsernameField, gbc);
        
        // Password field
        gbc.gridy = 2;
        JLabel passwordLabel = new JLabel("Mật khẩu (tối thiểu 6 ký tự):");
        passwordLabel.setFont(new Font("Segoe UI", Font.PLAIN, 14));
        passwordLabel.setForeground(TEXT_PRIMARY);
        panel.add(passwordLabel, gbc);
        
        gbc.gridy = 3;
        registerPasswordField = new JPasswordField(25);
        styleTextField(registerPasswordField);
        panel.add(registerPasswordField, gbc);
        
        // Display name field
        gbc.gridy = 4;
        JLabel displayNameLabel = new JLabel("Tên hiển thị:");
        displayNameLabel.setFont(new Font("Segoe UI", Font.PLAIN, 14));
        displayNameLabel.setForeground(TEXT_PRIMARY);
        panel.add(displayNameLabel, gbc);
        
        gbc.gridy = 5;
        registerDisplayNameField = new JTextField(25);
        styleTextField(registerDisplayNameField);
        panel.add(registerDisplayNameField, gbc);
        
        // Email field
        gbc.gridy = 6;
        JLabel emailLabel = new JLabel("Email (tùy chọn):");
        emailLabel.setFont(new Font("Segoe UI", Font.PLAIN, 14));
        emailLabel.setForeground(TEXT_SECONDARY);
        panel.add(emailLabel, gbc);
        
        gbc.gridy = 7;
        registerEmailField = new JTextField(25);
        styleTextField(registerEmailField);
        panel.add(registerEmailField, gbc);
        
        // Room field
        gbc.gridy = 8;
        JLabel roomLabel = new JLabel("Phòng hội thoại:");
        roomLabel.setFont(new Font("Segoe UI", Font.PLAIN, 14));
        roomLabel.setForeground(TEXT_PRIMARY);
        panel.add(roomLabel, gbc);
        
        gbc.gridy = 9;
        registerRoomField = new JTextField("demo", 25);
        styleTextField(registerRoomField);
        panel.add(registerRoomField, gbc);
        
        // Server field
        gbc.gridy = 10;
        JLabel serverLabel = new JLabel("Server:");
        serverLabel.setFont(new Font("Segoe UI", Font.PLAIN, 14));
        serverLabel.setForeground(TEXT_SECONDARY);
        panel.add(serverLabel, gbc);
        
        gbc.gridy = 11;
        registerServerField = new JTextField("localhost", 25);
        styleTextField(registerServerField);
        panel.add(registerServerField, gbc);
        
        // Register button
        gbc.gridy = 12;
        gbc.insets = new Insets(20, 0, 0, 0);
        registerButton = createStyledButton("Đăng ký", ACCENT_BLUE);
        registerButton.addActionListener(e -> performRegister());
        panel.add(registerButton, gbc);
        
        return panel;
    }
    
    private void styleTextField(JTextField field) {
        field.setFont(new Font("Segoe UI", Font.PLAIN, 14));
        field.setBackground(BG_SECONDARY);
        field.setForeground(TEXT_PRIMARY);
        field.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(new Color(60, 60, 60), 1),
            new EmptyBorder(10, 12, 10, 12)
        ));
        field.setCaretColor(TEXT_PRIMARY);
    }
    
    private JButton createStyledButton(String text, Color bgColor) {
        JButton button = new JButton(text) {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                
                int w = getWidth();
                int h = getHeight();
                int arc = 20;
                
                boolean hovered = getModel().isRollover();
                Color color = hovered ? ACCENT_BLUE_LIGHT : bgColor;
                
                if (hovered || getModel().isPressed()) {
                    g2.setColor(new Color(0, 0, 0, 40));
                    g2.fillRoundRect(2, 3, w - 2, h - 2, arc, arc);
                }
                
                GradientPaint gradient = new GradientPaint(
                    0, 0, color,
                    0, h, color.darker()
                );
                g2.setPaint(gradient);
                g2.fillRoundRect(0, 0, w, h, arc, arc);
                
                g2.setStroke(new BasicStroke(1.5f));
                g2.setColor(new Color(color.getRed(), color.getGreen(), color.getBlue(), 150));
                g2.drawRoundRect(0, 0, w - 1, h - 1, arc, arc);
                
                g2.setColor(TEXT_PRIMARY);
                g2.setFont(new Font("Segoe UI", Font.BOLD, 14));
                FontMetrics fm = g2.getFontMetrics();
                int textX = (w - fm.stringWidth(text)) / 2;
                int textY = (h - fm.getHeight()) / 2 + fm.getAscent();
                g2.drawString(text, textX, textY);
                
                g2.dispose();
            }
        };
        button.setPreferredSize(new Dimension(200, 45));
        button.setFocusPainted(false);
        button.setBorderPainted(false);
        button.setContentAreaFilled(false);
        button.setOpaque(false);
        button.setCursor(new Cursor(Cursor.HAND_CURSOR));
        return button;
    }
    
    private void performLogin() {
        String username = loginUsernameField.getText().trim();
        String password = new String(loginPasswordField.getPassword());
        String room = loginRoomField.getText().trim();
        String server = loginServerField.getText().trim();
        
        if (username.isEmpty()) {
            showError("Vui lòng nhập tên đăng nhập!");
            loginUsernameField.requestFocus();
            return;
        }
        if (password.isEmpty()) {
            showError("Vui lòng nhập mật khẩu!");
            loginPasswordField.requestFocus();
            return;
        }
        if (room.isEmpty()) {
            showError("Vui lòng nhập tên phòng!");
            loginRoomField.requestFocus();
            return;
        }
        
        // Disable button during login
        loginButton.setEnabled(false);
        loginButton.setText("Đang đăng nhập...");
        
        // Create final variables for lambda
        final String finalRoom = room;
        final String finalServer = server;
        
        // Perform login in background thread
        new Thread(() -> {
            try {
                // Connect to RMI
                int rmiPort = 1099;
                var reg = LocateRegistry.getRegistry(finalServer, rmiPort);
                ConferenceService svc = (ConferenceService) reg.lookup("conference");
                
                // Call login API
                String displayName = svc.login(username, password);
                
                SwingUtilities.invokeLater(() -> {
                    if (displayName != null) {
                        this.displayName = displayName;
                        this.room = finalRoom;
                        this.server = finalServer;
                        this.conferenceService = svc;
                        this.joined = true;
                        dispose();
                    } else {
                        // Thông báo lỗi rõ ràng hơn
                        showError("Đăng nhập thất bại!\n\n" +
                                "Có thể do:\n" +
                                "• Tên đăng nhập không đúng hoặc chưa được đăng ký\n" +
                                "• Mật khẩu không đúng\n\n" +
                                "Vui lòng:\n" +
                                "• Kiểm tra lại tên đăng nhập và mật khẩu\n" +
                                "• Đảm bảo tài khoản đã được đăng ký\n" +
                                "• Thử đăng ký tài khoản mới nếu chưa có");
                        loginButton.setEnabled(true);
                        loginButton.setText("Đăng nhập");
                    }
                });
            } catch (Exception e) {
                SwingUtilities.invokeLater(() -> {
                    showError("Lỗi kết nối: " + e.getMessage());
                    e.printStackTrace();
                    loginButton.setEnabled(true);
                    loginButton.setText("Đăng nhập");
                });
            }
        }).start();
    }
    
    private void performRegister() {
        String username = registerUsernameField.getText().trim();
        String password = new String(registerPasswordField.getPassword());
        String displayName = registerDisplayNameField.getText().trim();
        String email = registerEmailField.getText().trim();
        String room = registerRoomField.getText().trim();
        String server = registerServerField.getText().trim();
        
        if (username.isEmpty()) {
            showError("Vui lòng nhập tên đăng nhập!");
            registerUsernameField.requestFocus();
            return;
        }
        if (password.length() < 6) {
            showError("Mật khẩu phải có ít nhất 6 ký tự!");
            registerPasswordField.requestFocus();
            return;
        }
        if (displayName.isEmpty()) {
            displayName = username; // Use username as display name
        }
        if (room.isEmpty()) {
            showError("Vui lòng nhập tên phòng!");
            registerRoomField.requestFocus();
            return;
        }
        
        // Disable button during registration
        registerButton.setEnabled(false);
        registerButton.setText("Đang đăng ký...");
        
        // Create final variable for lambda
        final String finalDisplayName = displayName;
        final String finalRoom = room;
        final String finalServer = server;
        
        // Perform registration in background thread
        new Thread(() -> {
            try {
                // Connect to RMI
                int rmiPort = 1099;
                var reg = LocateRegistry.getRegistry(finalServer, rmiPort);
                ConferenceService svc = (ConferenceService) reg.lookup("conference");
                
                // Call register API
                String resultDisplayName = svc.register(username, password, finalDisplayName, email);
                
                SwingUtilities.invokeLater(() -> {
                    if (resultDisplayName != null) {
                        this.displayName = resultDisplayName;
                        this.room = finalRoom;
                        this.server = finalServer;
                        this.conferenceService = svc;
                        this.joined = true;
                        dispose();
                    } else {
                        // Thông báo lỗi rõ ràng hơn
                        showError("Đăng ký thất bại!\n\n" +
                                "Tên đăng nhập \"" + username + "\" đã tồn tại trong hệ thống.\n\n" +
                                "Vui lòng:\n" +
                                "• Chọn tên đăng nhập khác (ví dụ: " + username + "1, " + username + "_new)\n" +
                                "• Hoặc đăng nhập với tài khoản này nếu bạn đã đăng ký trước đó\n\n" +
                                "Lưu ý: Tên đăng nhập phân biệt chữ hoa/thường.");
                        registerButton.setEnabled(true);
                        registerButton.setText("Đăng ký");
                    }
                });
            } catch (Exception e) {
                SwingUtilities.invokeLater(() -> {
                    showError("Lỗi kết nối: " + e.getMessage());
                    e.printStackTrace();
                    registerButton.setEnabled(true);
                    registerButton.setText("Đăng ký");
                });
            }
        }).start();
    }
    
    private void showError(String message) {
        JOptionPane.showMessageDialog(this, message, "Lỗi", JOptionPane.ERROR_MESSAGE);
    }
    
    public String getDisplayName() {
        return displayName;
    }
    
    public String getRoom() {
        return room;
    }
    
    public String getServer() {
        return server;
    }
    
    public ConferenceService getConferenceService() {
        return conferenceService;
    }
    
    public boolean isJoined() {
        return joined;
    }
}
