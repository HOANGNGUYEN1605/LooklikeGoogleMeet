package com.example.rtpav.client.media;

import com.github.sarxos.webcam.Webcam;
import com.github.sarxos.webcam.WebcamException;

import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageOutputStream;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.InputStream;
import java.io.ByteArrayOutputStream;
import java.util.Iterator;

/**
 * VideoCapture với webcam thật:
 * - Khi camera OFF -> trả về ảnh avatar.
 * - Khi camera ON  -> capture từ webcam thật.
 */
public class VideoCapture implements AutoCloseable {
    private static final String[] AVATAR_CANDIDATES = {
            "/avatar_default.png",
            "/avatar.png"
    };

    private final BufferedImage avatar;
    private volatile boolean camOn = false;
    private Webcam webcam = null;
    private final Dimension captureSize = new Dimension(640, 480);
    private volatile boolean webcamInitialized = false;
    private static final float JPEG_QUALITY = 0.5f; // Giảm quality xuống 50% để giảm bandwidth và CPU tối đa
    private static final int MAX_JPEG_SIZE = 30 * 1024; // Tối đa 30KB cho mỗi frame

    public VideoCapture() {
        this.avatar = loadAvatarOrFallback();
    }

    public void setCamOn(boolean on) {
        if (this.camOn == on) return; // No change
        
        this.camOn = on;
        
        if (on) {
            // Bật camera - khởi tạo webcam nếu chưa có
            initializeWebcam();
        } else {
            // Tắt camera - ĐÓNG webcam ngay để giải phóng cho client khác
            closeWebcam();
        }
    }
    
    /**
     * Đóng webcam nhưng giữ reference để có thể mở lại
     */
    private synchronized void closeWebcam() {
        if (webcam != null && webcam.isOpen()) {
            try {
                webcam.close();
                System.out.println("Webcam closed and released");
            } catch (Exception e) {
                System.err.println("Error closing webcam: " + e.getMessage());
            }
        }
    }

    public boolean isCamOn() {
        return camOn;
    }

    /**
     * Khởi tạo webcam với retry logic
     */
    private synchronized void initializeWebcam() {
        // Nếu đã có webcam và đang mở, không cần làm gì
        if (webcam != null && webcam.isOpen()) {
            return;
        }
        
        // Nếu đã có webcam nhưng đang đóng, thử mở lại
        if (webcamInitialized && webcam != null && !webcam.isOpen()) {
            try {
                webcam.open();
                System.out.println("Webcam reopened successfully");
                return;
            } catch (Exception e) {
                System.err.println("Failed to reopen webcam, will try to get new instance: " + e.getMessage());
                // Nếu không mở được, thử lấy webcam mới
                try {
                    webcam.close();
                } catch (Exception ignore) {}
                webcam = null;
                webcamInitialized = false;
            }
        }

        // Lấy webcam mới hoặc lần đầu tiên
        try {
            // Đợi một chút để đảm bảo webcam đã được giải phóng hoàn toàn
            Thread.sleep(200);
            
            // Tìm webcam mặc định
            webcam = Webcam.getDefault();
            
            if (webcam == null) {
                System.err.println("No webcam found!");
                return;
            }

            // Thiết lập kích thước capture
            Dimension[] sizes = webcam.getViewSizes();
            if (sizes != null && sizes.length > 0) {
                // Tìm size gần nhất với 640x480
                Dimension bestSize = captureSize;
                for (Dimension size : sizes) {
                    if (size.width >= 640 && size.height >= 480) {
                        bestSize = size;
                        break;
                    }
                }
                webcam.setViewSize(bestSize);
            } else {
                webcam.setViewSize(captureSize);
            }

            // Mở webcam với retry
            int maxRetries = 3;
            for (int i = 0; i < maxRetries; i++) {
                try {
                    webcam.open();
                    webcamInitialized = true;
                    System.out.println("Webcam initialized: " + webcam.getName() + 
                                     " @ " + webcam.getViewSize());
                    return;
                } catch (WebcamException e) {
                    if (i < maxRetries - 1) {
                        System.err.println("Webcam busy, retrying in 500ms... (attempt " + (i + 1) + "/" + maxRetries + ")");
                        Thread.sleep(500);
                    } else {
                        throw e;
                    }
                }
            }
        } catch (WebcamException e) {
            System.err.println("Webcam error (may be in use by another application): " + e.getMessage());
            System.err.println("Please close other applications using the webcam and try again.");
            webcam = null;
            webcamInitialized = false;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            System.err.println("Interrupted while initializing webcam");
            webcam = null;
            webcamInitialized = false;
        } catch (Exception e) {
            System.err.println("Failed to initialize webcam: " + e.getMessage());
            e.printStackTrace();
            webcam = null;
            webcamInitialized = false;
        }
    }

    /** Trả về JPEG (avatar nếu OFF, webcam nếu ON). */
    public byte[] captureJpeg() {
        try {
            BufferedImage img;
            
            if (camOn && webcam != null) {
                // Kiểm tra webcam có đang mở không
                if (!webcam.isOpen()) {
                    // Webcam bị đóng, thử mở lại nếu camera đang bật
                    synchronized (this) {
                        if (camOn && webcam != null && !webcam.isOpen()) {
                            try {
                                webcam.open();
                            } catch (Exception e) {
                                // Nếu không mở được, trả về avatar
                                img = avatar;
                            }
                        }
                    }
                }
                
                // Capture từ webcam thật
                if (webcam != null && webcam.isOpen()) {
                    try {
                        img = webcam.getImage();
                        if (img == null) {
                            // Webcam chưa sẵn sàng, trả về avatar
                            img = avatar;
                        }
                    } catch (Exception e) {
                        // Lỗi khi capture, không đóng webcam, chỉ trả về avatar
                        System.err.println("Error getting webcam image (using avatar): " + e.getMessage());
                        img = avatar;
                    }
                } else {
                    img = avatar;
                }
            } else {
                // Camera OFF hoặc webcam không có -> trả về avatar
                img = avatar;
            }
            
            if (img == null) {
                return null;
            }
            
            // Resize xuống 480x360 để giảm bandwidth và CPU tối đa
            BufferedImage resizedImg = img;
            int targetWidth = 480;
            int targetHeight = 360;
            if (img.getWidth() > targetWidth || img.getHeight() > targetHeight) {
                int newWidth = Math.min(img.getWidth(), targetWidth);
                int newHeight = Math.min(img.getHeight(), targetHeight);
                // Giữ tỷ lệ khung hình
                double aspectRatio = (double) img.getWidth() / img.getHeight();
                if (newWidth / aspectRatio > newHeight) {
                    newWidth = (int) (newHeight * aspectRatio);
                } else {
                    newHeight = (int) (newWidth / aspectRatio);
                }
                
                Image scaled = img.getScaledInstance(newWidth, newHeight, Image.SCALE_SMOOTH);
                resizedImg = new BufferedImage(newWidth, newHeight, BufferedImage.TYPE_INT_RGB);
                Graphics2D g2d = resizedImg.createGraphics();
                g2d.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
                g2d.drawImage(scaled, 0, 0, null);
                g2d.dispose();
            }
            
            // Encode JPEG với quality tối ưu
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            try {
                Iterator<ImageWriter> writers = ImageIO.getImageWritersByFormatName("jpeg");
                if (writers.hasNext()) {
                    ImageWriter writer = writers.next();
                    ImageWriteParam param = writer.getDefaultWriteParam();
                    if (param.canWriteCompressed()) {
                        param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
                        param.setCompressionQuality(JPEG_QUALITY);
                    }
                    try (ImageOutputStream ios = ImageIO.createImageOutputStream(out)) {
                        writer.setOutput(ios);
                        writer.write(null, new javax.imageio.IIOImage(resizedImg, null, null), param);
                    }
                    writer.dispose();
                } else {
                    // Fallback: dùng ImageIO.write nếu không có writer hỗ trợ compression
                    ImageIO.write(resizedImg, "jpeg", out);
                }
            } catch (Exception e) {
                // Fallback: dùng ImageIO.write nếu có lỗi
                ImageIO.write(resizedImg, "jpeg", out);
            }
            
            byte[] jpegData = out.toByteArray();
            
            // Nếu vẫn quá lớn, giảm quality thêm
            if (jpegData.length > MAX_JPEG_SIZE) {
                float lowerQuality = JPEG_QUALITY * 0.7f; // Giảm thêm 30%
                out = new ByteArrayOutputStream();
                try {
                    Iterator<ImageWriter> writers = ImageIO.getImageWritersByFormatName("jpeg");
                    if (writers.hasNext()) {
                        ImageWriter writer = writers.next();
                        ImageWriteParam param = writer.getDefaultWriteParam();
                        if (param.canWriteCompressed()) {
                            param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
                            param.setCompressionQuality(lowerQuality);
                        }
                        try (ImageOutputStream ios = ImageIO.createImageOutputStream(out)) {
                            writer.setOutput(ios);
                            writer.write(null, new javax.imageio.IIOImage(resizedImg, null, null), param);
                        }
                        writer.dispose();
                    } else {
                        ImageIO.write(resizedImg, "jpeg", out);
                    }
                } catch (Exception e) {
                    ImageIO.write(resizedImg, "jpeg", out);
                }
                jpegData = out.toByteArray();
            }
            
            return jpegData;
        } catch (Exception e) {
            System.err.println("Error capturing frame: " + e.getMessage());
            return null;
        }
    }

    @Override 
    public void close() {
        if (webcam != null) {
            try {
                if (webcam.isOpen()) {
                    webcam.close();
                }
            } catch (Exception e) {
                System.err.println("Error closing webcam: " + e.getMessage());
            }
            webcam = null;
            webcamInitialized = false;
        }
    }

    // ================== helpers ==================

    private BufferedImage loadAvatarOrFallback() {
        // Thử lần lượt các path ứng viên trong classpath
        for (String path : AVATAR_CANDIDATES) {
            try (InputStream in = VideoCapture.class.getResourceAsStream(path)) {
                if (in != null) {
                    BufferedImage img = ImageIO.read(in);
                    if (img != null) return img;
                }
            } catch (Exception ignore) { /* thử path tiếp theo */ }
        }
        // Fallback: vẽ avatar "giả lập"
        return drawFallbackAvatar(640, 480);
    }

    private static BufferedImage drawFallbackAvatar(int w, int h) {
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            // nền
            g.setColor(new Color(233, 240, 247));
            g.fillRect(0, 0, w, h);
            // đầu
            g.setColor(new Color(96, 116, 140));
            int headSize = Math.min(w, h) / 2;
            g.fillOval((w - headSize) / 2, h / 8, headSize, headSize);
            // thân
            int bodyW = (int)(w * 0.45);
            int bodyH = (int)(h * 0.35);
            int bodyX = (w - bodyW) / 2;
            int bodyY = (int)(h * 0.55);
            g.fillRoundRect(bodyX, bodyY, bodyW, bodyH, 60, 60);
        } finally {
            g.dispose();
        }
        return img;
    }
}
