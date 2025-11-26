package com.example.rtpav.client.ui;

import javax.swing.*;
import java.awt.*;

public class VideoRenderer extends JPanel {
    private volatile Image image;

    public VideoRenderer() {
        setPreferredSize(new Dimension(480, 270));
    }

    public void showAvatar(Image avatar) {
        this.image = avatar;
        repaint();
    }

    public void updateFrame(byte[] jpeg) {
        try {
            ImageIcon icon = new ImageIcon(jpeg);
            this.image = icon.getImage();
            repaint();
        } catch (Exception ignore) {}
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        if (image != null) {
            g.drawImage(image, 0, 0, getWidth(), getHeight(), null);
        } else {
            g.setColor(Color.GRAY);
            g.fillRect(0,0,getWidth(),getHeight());
            g.setColor(Color.BLACK);
            g.drawString("No video", 10, 20);
        }
    }
}
