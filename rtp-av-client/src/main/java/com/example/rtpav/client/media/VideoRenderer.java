package com.example.rtpav.client.media;

import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;

public class VideoRenderer extends JPanel {
    private BufferedImage frame;
    private BufferedImage avatar;
    private boolean showAvatar = false;
    
    public BufferedImage getCurrentFrame() {
        return frame;
    }
    private static final Color BG_COLOR = new Color(20, 20, 20);
    private static final String NO_VIDEO_TEXT = "No Video";

    public VideoRenderer() {
        setBackground(BG_COLOR);
        setOpaque(true);
        loadDefaultAvatar();
    }

    private String userName; // Tên người dùng để tạo avatar
    
    public void setUserName(String userName) {
        this.userName = userName;
        if (userName != null && !userName.isEmpty()) {
            createAvatarFromName(userName);
        } else {
            loadDefaultAvatar();
        }
    }
    
    private void loadDefaultAvatar() {
        // Tạo avatar mặc định hình tròn với chữ "?"
        createAvatarFromName("?");
    }
    
    /**
     * Tạo avatar hình tròn với chữ cái đầu của tên - Google Meet style
     */
    private void createAvatarFromName(String name) {
        // Lấy chữ cái đầu (uppercase)
        String initial = name != null && !name.isEmpty() 
            ? name.trim().substring(0, 1).toUpperCase() 
            : "?";
        
        // Tạo màu nền dựa trên hash của tên (để mỗi người có màu khác nhau)
        Color bgColor = generateColorFromName(name != null ? name : "?");
        
        // Tạo avatar hình tròn
        int size = 640; // Kích thước lớn để chất lượng tốt
        avatar = new BufferedImage(size, size, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = avatar.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            
            // Nền trong suốt (sẽ được vẽ bởi paintComponent)
            // Vẽ hình tròn với màu nền
            g.setColor(bgColor);
            g.fillOval(0, 0, size, size);
            
            // Vẽ chữ cái đầu (màu trắng)
            g.setColor(Color.WHITE);
            Font font = new Font("Google Sans", Font.BOLD, size / 2); // Font lớn, chiếm ~50% kích thước
            g.setFont(font);
            FontMetrics fm = g.getFontMetrics();
            int textWidth = fm.stringWidth(initial);
            int textHeight = fm.getAscent();
            // Căn giữa chữ
            int x = (size - textWidth) / 2;
            int y = (size + textHeight) / 2 - fm.getDescent();
            g.drawString(initial, x, y);
        } finally {
            g.dispose();
        }
    }
    
    /**
     * Tạo màu dựa trên hash của tên - mỗi tên sẽ có màu khác nhau nhưng nhất quán
     */
    private Color generateColorFromName(String name) {
        // Danh sách màu đẹp giống Google Meet
        Color[] colors = {
            new Color(26, 115, 232),  // Blue
            new Color(52, 168, 83),   // Green
            new Color(251, 188, 4),  // Yellow
            new Color(234, 67, 53),   // Red
            new Color(155, 81, 224), // Purple
            new Color(255, 152, 0),  // Orange
            new Color(0, 172, 193),  // Cyan
            new Color(124, 179, 66), // Light Green
            new Color(171, 71, 188), // Deep Purple
            new Color(255, 87, 34),  // Deep Orange
            new Color(0, 150, 136),  // Teal
            new Color(63, 81, 181),  // Indigo
        };
        
        // Hash tên để chọn màu
        int hash = name.hashCode();
        int index = Math.abs(hash) % colors.length;
        return colors[index];
    }

    public void updateFrame(BufferedImage img) {
        this.frame = img;
        this.showAvatar = false;
        SwingUtilities.invokeLater(this::repaint);
    }

    public void showAvatar() {
        this.frame = avatar;
        this.showAvatar = true;
        SwingUtilities.invokeLater(this::repaint);
    }
    
    /**
     * Set custom avatar image from file
     */
    public void setCustomAvatar(BufferedImage img) {
        if (img != null) {
            this.avatar = img;
            // Nếu đang hiển thị avatar, cập nhật ngay
            if (this.showAvatar) {
                this.frame = avatar;
                SwingUtilities.invokeLater(this::repaint);
            }
        }
    }
    
    /**
     * Get current avatar image
     */
    public BufferedImage getAvatar() {
        return avatar;
    }
    
    /**
     * Check if currently showing avatar (not video)
     */
    public boolean isShowingAvatar() {
        return showAvatar;
    }

    @Override 
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2 = (Graphics2D) g.create();
        
        // Enable anti-aliasing for better quality
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g2.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        
        int w = getWidth();
        int h = getHeight();
        
        // Fill background
        g2.setColor(BG_COLOR);
        g2.fillRect(0, 0, w, h);
        
        if (frame != null) {
            // Calculate aspect ratio and center the image
            double imgAspect = (double) frame.getWidth() / frame.getHeight();
            double panelAspect = (double) w / h;
            
            int drawWidth, drawHeight, drawX, drawY;
            
            if (imgAspect > panelAspect) {
                // Image is wider - fit to width
                drawWidth = w;
                drawHeight = (int) (w / imgAspect);
                drawX = 0;
                drawY = (h - drawHeight) / 2;
            } else {
                // Image is taller - fit to height
                drawHeight = h;
                drawWidth = (int) (h * imgAspect);
                drawX = (w - drawWidth) / 2;
                drawY = 0;
            }
            
            // Draw image with better quality
            g2.drawImage(frame, drawX, drawY, drawWidth, drawHeight, null);
        } else {
            // Show "No Video" message or avatar
            if (showAvatar && avatar != null) {
                // Hiển thị avatar hình tròn - Google Meet style
                // Tính kích thước để fit vào panel (giữ tỷ lệ hình tròn)
                int size = Math.min(w, h);
                int x = (w - size) / 2;
                int y = (h - size) / 2;
                
                // Vẽ avatar hình tròn
                g2.setClip(new java.awt.geom.Ellipse2D.Float(x, y, size, size));
                g2.drawImage(avatar, x, y, size, size, null);
                g2.setClip(null);
            } else {
                // Show "No Video" message
                g2.setColor(new Color(150, 150, 150));
                g2.setFont(new Font("Segoe UI", Font.PLAIN, 16));
                FontMetrics fm = g2.getFontMetrics();
                int textWidth = fm.stringWidth(NO_VIDEO_TEXT);
                int textHeight = fm.getHeight();
                g2.drawString(NO_VIDEO_TEXT, (w - textWidth) / 2, (h + textHeight) / 2);
            }
        }
        
        g2.dispose();
    }
}
