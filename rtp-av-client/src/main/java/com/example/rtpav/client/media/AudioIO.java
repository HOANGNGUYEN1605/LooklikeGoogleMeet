package com.example.rtpav.client.media;

import javax.sound.sampled.*;

public class AudioIO implements AutoCloseable {

    public static final AudioFormat FORMAT = new AudioFormat(
            AudioFormat.Encoding.PCM_SIGNED,
            16000f, 16, 1, 2, 16000f, false // 16k mono 16-bit, little-endian
    );

    private TargetDataLine mic;
    private SourceDataLine speaker;

    public void open() throws LineUnavailableException {
        DataLine.Info micInfo = new DataLine.Info(TargetDataLine.class, FORMAT);
        DataLine.Info spkInfo = new DataLine.Info(SourceDataLine.class, FORMAT);
        mic = (TargetDataLine) AudioSystem.getLine(micInfo);
        speaker = (SourceDataLine) AudioSystem.getLine(spkInfo);
        mic.open(FORMAT);
        speaker.open(FORMAT);
    }

    public void start() {
        if (mic != null) mic.start();
        if (speaker != null) speaker.start();
    }

    public int readMic(byte[] buf) {
        if (mic == null || !mic.isOpen()) return -1;
        // Không check isActive() vì có thể mic đang trong quá trình khởi động
        // Nếu mic không active, read() sẽ trả về 0 hoặc throw exception, không phải -1
        try {
            return mic.read(buf, 0, buf.length);
        } catch (Exception e) {
            // Nếu có lỗi khi đọc, trả về -1
            return -1;
        }
    }

    public void play(byte[] buf, int len) {
        if (speaker != null) speaker.write(buf, 0, len);
    }
    
    /**
     * Dừng mic line nhưng vẫn giữ speaker để nghe
     */
    public void stopMic() {
        if (mic != null && mic.isOpen() && mic.isActive()) {
            mic.stop();
            mic.flush(); // Xóa buffer để đảm bảo không còn dữ liệu
        }
    }
    
    /**
     * Bật lại mic line (sau khi đã dừng bằng stopMic)
     */
    public void startMic() {
        if (mic != null && mic.isOpen()) {
            if (!mic.isActive()) {
                mic.start();
                System.out.println("[AUDIO] Mic line started");
            } else {
                System.out.println("[AUDIO] Mic line already active");
            }
        } else {
            System.err.println("[AUDIO] WARNING: Cannot start mic - mic is null or not open");
        }
    }

    @Override
    public void close() {
        if (mic != null) { mic.stop(); mic.close(); }
        if (speaker != null) { speaker.drain(); speaker.stop(); speaker.close(); }
    }
}
