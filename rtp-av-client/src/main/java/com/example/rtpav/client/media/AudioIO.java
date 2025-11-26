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
        if (mic == null) return -1;
        return mic.read(buf, 0, buf.length);
    }

    public void play(byte[] buf, int len) {
        if (speaker != null) speaker.write(buf, 0, len);
    }

    @Override
    public void close() {
        if (mic != null) { mic.stop(); mic.close(); }
        if (speaker != null) { speaker.drain(); speaker.stop(); speaker.close(); }
    }
}
