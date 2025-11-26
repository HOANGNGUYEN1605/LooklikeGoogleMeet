package com.example.rtpav.client;

import com.example.rtpav.client.media.VideoCapture;
import com.example.rtpav.rmi.ClientCallback;
import com.example.rtpav.rmi.ConferenceService;

import javax.imageio.ImageIO;
import javax.swing.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.net.*;
import java.nio.ByteBuffer;
import java.rmi.registry.LocateRegistry;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

public class ClientMain {
    public static void main(String[] args) throws Exception {
        String serverHost = "localhost";
        int rmiPort = 1099;
        int serverRtp = 5004;
        int localRtp = 6000;
        String roomId = "demo";
        String name = "Alice";
        long ssrc = System.currentTimeMillis();

        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "--server" -> serverHost = args[++i];
                case "--rmi" -> rmiPort = Integer.parseInt(args[++i]);
                case "--server-rtp" -> serverRtp = Integer.parseInt(args[++i]);
                case "--rtp" -> localRtp = Integer.parseInt(args[++i]);
                case "--room" -> roomId = args[++i];
                case "--name" -> name = args[++i];
            }
        }

        final String fServer = serverHost;
        final int fRmiPort = rmiPort;
        final int fServerRtp = serverRtp;
        final int fLocalRtp = localRtp;
        final String fRoom = roomId;
        final String fName = name;
        final long fSsrc = ssrc;

        SwingUtilities.invokeAndWait(() -> {
            try {
                var reg = LocateRegistry.getRegistry(fServer, fRmiPort);
                ConferenceService svc = (ConferenceService) reg.lookup("conference");

                InetSocketAddress myRtp = NetUtil.endpoint("0.0.0.0", fLocalRtp);
                InetSocketAddress serverRtpAddr = new InetSocketAddress(fServer, fServerRtp);

                ClientUI ui = new ClientUI(fName, fRoom, fSsrc, svc, myRtp);
                ClientCallback cb = new ClientCallbackImpl(ui);

                // gửi join
                svc.join(fRoom, fSsrc, myRtp.getPort(), (com.example.rtpav.rmi.ClientCallback) cb);
                ui.addChat("[SYSTEM]", "RMI connected to " + fServer + ":" + fRmiPort);
                ui.addChat("[SYSTEM]", "Joined room " + fRoom + ", UDP bind=" + myRtp);

                // UDP socket để gửi/nhận video
                DatagramSocket udpSocket = new DatagramSocket(fLocalRtp);
                udpSocket.setSendBufferSize(1 << 20);
                udpSocket.setReceiveBufferSize(1 << 20);

                // camera loop
                VideoCapture cam = new VideoCapture();
                var pool = new ScheduledThreadPoolExecutor(2);

                // Audio handler - khai báo trước để cả UDP receiver và hooks đều dùng được
                com.example.rtpav.client.media.AudioIO audio = null;
                final com.example.rtpav.client.media.AudioIO[] audioRef = {audio};

                // UDP receiver để nhận video từ peers
                final boolean[] firstFrameReceived = {false};
                pool.submit(() -> {
                    byte[] buf = new byte[64 * 1024];
                    DatagramPacket pkt = new DatagramPacket(buf, buf.length);
                    System.out.println("[CLIENT] UDP receiver listening on port " + fLocalRtp);
                    
                    while (!Thread.currentThread().isInterrupted()) {
                        try {
                            udpSocket.setSoTimeout(1000); // Timeout 1 giây
                            udpSocket.receive(pkt);
                            if (pkt.getLength() < 9) continue;

                            ByteBuffer bb = ByteBuffer.wrap(pkt.getData(), 0, pkt.getLength());
                            long peerSsrc = bb.getLong();
                            byte mediaType = bb.get();

                            if (mediaType == 0) { // video
                                int payloadLen = pkt.getLength() - 9;
                                byte[] jpeg = new byte[payloadLen];
                                System.arraycopy(pkt.getData(), 9, jpeg, 0, payloadLen);

                                try {
                                    BufferedImage img = ImageIO.read(new ByteArrayInputStream(jpeg));
                                    if (img != null) {
                                        // Cập nhật video cho peer cụ thể
                                        ui.updatePeerVideo(peerSsrc, img);
                                        if (!firstFrameReceived[0]) {
                                            System.out.println("[CLIENT] Received video from peer SSRC=" + peerSsrc);
                                            firstFrameReceived[0] = true;
                                        }
                                    }
                                } catch (Exception e) {
                                    // Ignore decode errors
                                }
                            } else if (mediaType == 1) { // audio
                                int payloadLen = pkt.getLength() - 9;
                                byte[] audioData = new byte[payloadLen];
                                System.arraycopy(pkt.getData(), 9, audioData, 0, payloadLen);
                                
                                // Phát audio từ peer
                                try {
                                    if (audioRef[0] == null) {
                                        // Khởi tạo audio nếu chưa có (để phát audio từ peers)
                                        audioRef[0] = new com.example.rtpav.client.media.AudioIO();
                                        audioRef[0].open();
                                        audioRef[0].start();
                                    }
                                    audioRef[0].play(audioData, payloadLen);
                                } catch (Exception e) {
                                    System.err.println("[CLIENT] Error playing audio: " + e.getMessage());
                                }
                            }
                        } catch (SocketTimeoutException e) {
                            // Timeout là bình thường, tiếp tục loop
                            continue;
                        } catch (SocketException e) {
                            if (!udpSocket.isClosed()) {
                                System.err.println("[CLIENT] UDP receive error: " + e.getMessage());
                            }
                            break;
                        } catch (Exception e) {
                            System.err.println("[CLIENT] Error receiving UDP: " + e.getMessage());
                        }
                    }
                });
                
                // Timer để kiểm tra và tự động quay lại avatar nếu không nhận được video
                pool.scheduleAtFixedRate(() -> {
                    ui.checkAndShowAvatars();
                }, 2, 1, TimeUnit.SECONDS);
                
                ui.setHooks(new ClientUI.Hooks() {
                    @Override 
                    public void onToggleCamera(boolean on) { 
                        cam.setCamOn(on); 
                    }
                    
                    @Override 
                    public void onToggleMic(boolean on) {
                        try {
                            if (on) {
                                // Bật mic: khởi tạo AudioIO và bắt đầu gửi audio
                                if (audioRef[0] == null) {
                                    audioRef[0] = new com.example.rtpav.client.media.AudioIO();
                                    audioRef[0].open();
                                    audioRef[0].start();
                                }
                                
                                // Bắt đầu loop gửi audio
                                pool.submit(() -> {
                                    byte[] header = ByteBuffer.allocate(9).array();
                                    byte[] audioBuf = new byte[320]; // 10ms @ 16kHz mono 16-bit
                                    
                                    while (audioRef[0] != null && !Thread.currentThread().isInterrupted()) {
                                        try {
                                            int n = audioRef[0].readMic(audioBuf);
                                            if (n > 0) {
                                                // Tạo packet: [SSRC (8 bytes)] + [mediaType=1 (1 byte)] + [audio data]
                                                ByteBuffer bb = ByteBuffer.wrap(header);
                                                bb.putLong(fSsrc);
                                                bb.put((byte) 1); // mediaType = 1 (audio)
                                                
                                                byte[] packet = new byte[9 + n];
                                                System.arraycopy(header, 0, packet, 0, 9);
                                                System.arraycopy(audioBuf, 0, packet, 9, n);
                                                
                                                DatagramPacket udpPkt = new DatagramPacket(
                                                    packet, packet.length,
                                                    serverRtpAddr
                                                );
                                                udpSocket.send(udpPkt);
                                            } else {
                                                Thread.sleep(10); // 10ms
                                            }
                                        } catch (Exception e) {
                                            if (!Thread.currentThread().isInterrupted()) {
                                                System.err.println("[CLIENT] Error sending audio: " + e.getMessage());
                                            }
                                            break;
                                        }
                                    }
                                });
                            } else {
                                // Tắt mic: dừng gửi nhưng vẫn giữ speaker để nghe peers
                                // Không đóng audio để vẫn có thể phát audio từ peers
                            }
                        } catch (Exception e) {
                            System.err.println("[CLIENT] Error toggling mic: " + e.getMessage());
                        }
                    }
                    
                    @Override 
                    public void onSendChat(String msg) {
                        try { 
                            svc.sendChat(fRoom, fSsrc, fName, msg); 
                        } catch (Exception ignored) {} 
                    }
                    
                    @Override 
                    public void onClose() {
                        try { 
                            svc.leave(fSsrc); 
                        } catch (Exception ignored) {}
                        cam.close();
                        if (audioRef[0] != null) {
                            try {
                                audioRef[0].close();
                            } catch (Exception ignored) {}
                        }
                        udpSocket.close();
                        pool.shutdownNow();
                    }
                });

                // Video capture và gửi loop
                final boolean[] firstFrameSent = {false};
                final boolean[] wasCamOn = {false};
                pool.scheduleAtFixedRate(() -> {
                    try {
                        boolean currentlyOn = cam.isCamOn();
                        
                        // Nếu camera vừa tắt, hiển thị avatar
                        if (wasCamOn[0] && !currentlyOn) {
                            SwingUtilities.invokeLater(() -> ui.showSelfAvatar());
                            firstFrameSent[0] = false;
                        }
                        wasCamOn[0] = currentlyOn;
                        
                        if (!currentlyOn) {
                            return;
                        }
                        
                        byte[] jpeg = cam.captureJpeg();
                        if (jpeg == null || jpeg.length == 0) return;

                        // Hiển thị local
                        BufferedImage img = ImageIO.read(new ByteArrayInputStream(jpeg));
                        if (img != null) {
                            SwingUtilities.invokeLater(() -> ui.updateSelf(img));
                        }

                        // Gửi đến server để forward cho peers
                        ByteBuffer header = ByteBuffer.allocate(9);
                        header.putLong(fSsrc);
                        header.put((byte) 0); // mediaType = 0 (video)

                        byte[] packet = new byte[9 + jpeg.length];
                        System.arraycopy(header.array(), 0, packet, 0, 9);
                        System.arraycopy(jpeg, 0, packet, 9, jpeg.length);

                        DatagramPacket udpPkt = new DatagramPacket(
                            packet, packet.length, 
                            serverRtpAddr
                        );
                        udpSocket.send(udpPkt);
                        
                        if (!firstFrameSent[0]) {
                            System.out.println("[CLIENT] Sending video to server " + serverRtpAddr + ", SSRC=" + fSsrc);
                            firstFrameSent[0] = true;
                        }
                    } catch (Exception e) {
                        System.err.println("[CLIENT] Error sending video: " + e.getMessage());
                    }
                }, 0, 100, TimeUnit.MILLISECONDS);

                ui.setVisible(true);
            } catch (Exception e) {
                e.printStackTrace();
                JOptionPane.showMessageDialog(null, "Cannot start client: " + e, "Error",
                        JOptionPane.ERROR_MESSAGE);
            }
        });
    }
}
