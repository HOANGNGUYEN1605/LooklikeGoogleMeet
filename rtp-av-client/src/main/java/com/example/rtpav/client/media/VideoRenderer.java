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

    private void loadDefaultAvatar() {
        // Tạo avatar mặc định
        avatar = new BufferedImage(640, 480, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = avatar.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            // Nền
            g.setColor(new Color(233, 240, 247));
            g.fillRect(0, 0, 640, 480);
            // Đầu
            g.setColor(new Color(96, 116, 140));
            int headSize = 240;
            g.fillOval((640 - headSize) / 2, 60, headSize, headSize);
            // Thân
            int bodyW = 288;
            int bodyH = 168;
            int bodyX = (640 - bodyW) / 2;
            int bodyY = 264;
            g.fillRoundRect(bodyX, bodyY, bodyW, bodyH, 60, 60);
        } finally {
            g.dispose();
        }
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
                // Hiển thị avatar
                double imgAspect = (double) avatar.getWidth() / avatar.getHeight();
                double panelAspect = (double) w / h;
                
                int drawWidth, drawHeight, drawX, drawY;
                
                if (imgAspect > panelAspect) {
                    drawWidth = w;
                    drawHeight = (int) (w / imgAspect);
                    drawX = 0;
                    drawY = (h - drawHeight) / 2;
                } else {
                    drawHeight = h;
                    drawWidth = (int) (h * imgAspect);
                    drawX = (w - drawWidth) / 2;
                    drawY = 0;
                }
                
                g2.drawImage(avatar, drawX, drawY, drawWidth, drawHeight, null);
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
