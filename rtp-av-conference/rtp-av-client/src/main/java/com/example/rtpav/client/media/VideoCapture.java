package com.example.rtpav.client.media;

import com.github.sarxos.webcam.Webcam;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;

public class VideoCapture implements AutoCloseable {
    private final Webcam cam;

    public VideoCapture(int width, int height) {
        cam = Webcam.getDefault();
        if (cam != null) {
            Dimension d = new Dimension(width, height);
            cam.setViewSize(d);
            cam.open();
        }
    }

    public byte[] captureJpeg() {
        try {
            if (cam == null || !cam.isOpen()) return null;
            BufferedImage img = cam.getImage();
            if (img == null) return null;
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            ImageIO.write(img, "jpg", baos);
            return baos.toByteArray();
        } catch (Exception e) {
            return null;
        }
    }

    @Override
    public void close() {
        if (cam != null) cam.close();
    }
}
