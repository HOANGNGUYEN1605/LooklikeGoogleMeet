package com.example.rtpav.client;

import com.example.rtpav.client.NetUtil;
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
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

public class ClientMain {
    private static DatagramSocket createSocketWithBind(String clientIP, int port) {
        try {
            InetAddress bindAddr = InetAddress.getByName(clientIP);
            DatagramSocket socket = new DatagramSocket(port, bindAddr);
            System.out.println("[CLIENT] UDP socket bound to " + clientIP + ":" + port);
            return socket;
        } catch (Exception e) {
            System.err.println("[CLIENT] Failed to bind to " + clientIP + ", falling back to 0.0.0.0: " + e.getMessage());
            try {
                return new DatagramSocket(port);
            } catch (Exception e2) {
                throw new RuntimeException("Failed to create UDP socket on port " + port, e2);
            }
        }
    }
    
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
            // Hiển thị login dialog trước
            LoginDialog loginDialog = new LoginDialog(null, fServer);
            loginDialog.setVisible(true);
            
            // Nếu user không join (đóng dialog), thoát chương trình
            if (!loginDialog.isJoined()) {
                System.out.println("[CLIENT] User cancelled login, exiting...");
                System.exit(0);
                return;
            }
            
            // Lấy thông tin từ dialog
            final String actualName = loginDialog.getDisplayName();
            final String actualRoom = loginDialog.getRoom();
            final String actualServer = loginDialog.getServer();
            final TcpControlClient tcpClient = loginDialog.getTcpClient(); // TCP client (ưu tiên)
            final ConferenceService svc = loginDialog.getConferenceService(); // RMI fallback
            
            try {
                // Xác định IP thực của client để gửi cho server
                // Nếu client trên cùng máy server, dùng IP LAN thực thay vì 0.0.0.0
                String clientIP = "0.0.0.0";
                String clientLANIP = NetUtil.findLANIP();
                if (clientLANIP != null) {
                    // Kiểm tra xem client có trên cùng máy server không (so sánh IP)
                    if (actualServer.equals("localhost") || actualServer.equals("127.0.0.1") || 
                        (clientLANIP != null && actualServer.equals(clientLANIP))) {
                        // Client trên cùng máy server, dùng IP LAN thực
                        clientIP = clientLANIP;
                        System.out.println("[CLIENT] Running on same machine as server, using LAN IP: " + clientIP);
                    }
                }
                
                InetSocketAddress myRtp = NetUtil.endpoint(clientIP, fLocalRtp);
                InetSocketAddress serverRtpAddr = new InetSocketAddress(actualServer, fServerRtp);

                ClientUI ui = new ClientUI(actualName, actualRoom, fSsrc, svc, myRtp);
                ClientCallback cb = new ClientCallbackImpl(ui);

                // UDP Control Client - cho chat và peer updates (giảm lag)
                int udpControlPort = fLocalRtp + 10000; // Port riêng cho control (tránh conflict với RTP)
                InetSocketAddress serverControlAddr = new InetSocketAddress(actualServer, 5005); // Server UDP control port
                UdpControlClient udpControl = new UdpControlClient(udpControlPort, serverControlAddr, fSsrc, actualName, ui);
                Thread udpControlThread = new Thread(udpControl, "UdpControlClient");
                udpControlThread.setDaemon(true);
                udpControlThread.start();
                
                // Register control endpoint với server
                udpControl.registerEndpoint();
                // Keepalive mỗi 5 giây
                java.util.concurrent.ScheduledExecutorService keepalivePool = java.util.concurrent.Executors.newScheduledThreadPool(1);
                keepalivePool.scheduleAtFixedRate(() -> udpControl.registerEndpoint(), 5, 5, java.util.concurrent.TimeUnit.SECONDS);

                // Join room - ưu tiên dùng TCP (nhanh hơn, không lag)
                long joinStartTime = System.currentTimeMillis();
                java.util.Set<com.example.rtpav.rmi.PeerInfo> initialPeers = new java.util.HashSet<>();
                
                if (tcpClient != null) {
                    // Use TCP
                    initialPeers = tcpClient.join(actualRoom, fSsrc, actualName, myRtp.getPort());
                    long joinTime = System.currentTimeMillis() - joinStartTime;
                    ui.addChat("[SYSTEM]", "TCP connected to " + actualServer + ":5006");
                    ui.addChat("[SYSTEM]", "Joined room " + actualRoom + ", UDP bind=" + myRtp + " (took " + joinTime + "ms)");
                } else if (svc != null) {
                    // Fallback to RMI
                    svc.join(actualRoom, fSsrc, actualName, myRtp.getPort(), (com.example.rtpav.rmi.ClientCallback) cb);
                    long joinTime = System.currentTimeMillis() - joinStartTime;
                    ui.addChat("[SYSTEM]", "RMI connected to " + actualServer + ":" + fRmiPort);
                    ui.addChat("[SYSTEM]", "Joined room " + actualRoom + ", UDP bind=" + myRtp + " (took " + joinTime + "ms)");
                    // Get peers via RMI
                    try {
                        initialPeers = svc.getPeers(actualRoom);
                    } catch (Exception e) {
                        System.err.println("[CLIENT] Error getting peers via RMI: " + e.getMessage());
                    }
                }
                
                // Khai báo pool và cam trước để dùng cho polling
                VideoCapture cam = new VideoCapture();
                var pool = new ScheduledThreadPoolExecutor(4); // Tăng thread pool để hỗ trợ async operations
                
                // Set initial peers
                if (!initialPeers.isEmpty()) {
                    ui.setPeers(initialPeers);
                    ui.setLastPeerCount(initialPeers.size());
                    ui.addChat("[SYSTEM]", "Peers = " + initialPeers.size() + " (from TCP)");
                    System.out.println("[CLIENT] Initial peers count: " + initialPeers.size());
                    for (var p : initialPeers) {
                        System.out.println("[CLIENT]   Peer: SSRC=" + p.getSsrc() + ", name=" + p.getName() + ", endpoint=" + p.getRtpEndpoint());
                    }
                }
                
                // Polling để refresh peers (fallback nếu UDP peer updates không hoạt động)
                final TcpControlClient finalTcpClient = tcpClient;
                final ConferenceService finalSvc = svc;
                pool.scheduleAtFixedRate(() -> {
                    try {
                        java.util.Set<com.example.rtpav.rmi.PeerInfo> peers;
                        if (finalTcpClient != null) {
                            peers = finalTcpClient.getPeers(actualRoom);
                        } else if (finalSvc != null) {
                            peers = finalSvc.getPeers(actualRoom);
                        } else {
                            return;
                        }
                        
                        int currentCount = peers.size();
                        int lastCount = ui.getLastPeerCount();
                        if (currentCount != lastCount) {
                            System.out.println("[CLIENT] Peers changed (polling): " + lastCount + " -> " + currentCount);
                            ui.setPeers(peers);
                            ui.setLastPeerCount(currentCount);
                        }
                    } catch (Exception e) {
                        // Ignore
                    }
                }, 2, 2, TimeUnit.SECONDS); // Poll mỗi 2 giây

                // UDP socket để gửi/nhận video
                // Nếu client trên cùng máy server, bind vào IP LAN thực để nhận packet từ client khác
                final DatagramSocket udpSocket = clientIP.equals("0.0.0.0") 
                    ? new DatagramSocket(fLocalRtp)  // Client trên máy khác, bind vào tất cả interfaces
                    : createSocketWithBind(clientIP, fLocalRtp);  // Client trên cùng máy server, bind vào IP LAN thực
                // Tối ưu buffer sizes để giảm lag
                udpSocket.setSendBufferSize(2 << 20); // 2MB
                udpSocket.setReceiveBufferSize(2 << 20); // 2MB
                udpSocket.setReuseAddress(true);

                // Audio handler - khai báo trước để cả UDP receiver và hooks đều dùng được
                com.example.rtpav.client.media.AudioIO audio = null;
                final com.example.rtpav.client.media.AudioIO[] audioRef = {audio};
                // Track audio sending thread để tránh tạo nhiều thread
                final java.util.concurrent.atomic.AtomicReference<Thread> audioSendingThreadRef = new java.util.concurrent.atomic.AtomicReference<>();

                // UDP receiver để nhận video từ peers - tối ưu để giảm lag
                final boolean[] firstFrameReceived = {false};
                final java.util.concurrent.atomic.AtomicInteger pendingDecodes = new java.util.concurrent.atomic.AtomicInteger(0);
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
                                // Skip frame nếu có quá nhiều pending decodes (tránh lag)
                                if (pendingDecodes.get() > 3) {
                                    continue;
                                }
                                
                                int payloadLen = pkt.getLength() - 9;
                                byte[] jpeg = new byte[payloadLen];
                                System.arraycopy(pkt.getData(), 9, jpeg, 0, payloadLen);

                                // Decode JPEG trong background thread để không block
                                pendingDecodes.incrementAndGet();
                                pool.execute(() -> {
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
                                    } finally {
                                        pendingDecodes.decrementAndGet();
                                    }
                                });
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
                
                // Polling để tự động refresh peers list mỗi 1 giây (fallback nếu callback không hoạt động)
                // Giảm interval để phát hiện peers mới nhanh hơn
                pool.scheduleAtFixedRate(() -> {
                    try {
                        var peers = svc.getPeers(actualRoom);
                        int currentCount = peers.size();
                        int lastCount = ui.getLastPeerCount();
                        if (currentCount != lastCount) {
                            System.out.println("[CLIENT] Peers changed (polling): " + lastCount + " -> " + currentCount);
                            ui.setPeers(peers);
                            ui.setLastPeerCount(currentCount);
                        }
                    } catch (Exception e) {
                        // Ignore polling errors, sẽ retry lần sau
                    }
                }, 1, 1, TimeUnit.SECONDS); // 1 giây để phát hiện nhanh hơn
                
                ui.setHooks(new ClientUI.ExtendedHooks() {
                    @Override 
                    public void onToggleCamera(boolean on) { 
                        cam.setCamOn(on); 
                    }
                    
                    @Override 
                    public void onToggleMic(boolean on) {
                        // Chạy trong background thread để không block UI thread
                        pool.submit(() -> {
                            try {
                                if (on) {
                                    // Dừng thread cũ nếu đang chạy
                                    Thread oldThread = audioSendingThreadRef.getAndSet(null);
                                    if (oldThread != null && oldThread.isAlive()) {
                                        oldThread.interrupt();
                                        try {
                                            oldThread.join(100); // Đợi tối đa 100ms
                                        } catch (InterruptedException e) {
                                            Thread.currentThread().interrupt();
                                        }
                                    }
                                    
                                    // Bật mic: khởi tạo AudioIO và bắt đầu gửi audio
                                    if (audioRef[0] == null) {
                                        try {
                                            System.out.println("[CLIENT] Initializing AudioIO for microphone...");
                                            audioRef[0] = new com.example.rtpav.client.media.AudioIO();
                                            audioRef[0].open();
                                            audioRef[0].start();
                                            System.out.println("[CLIENT] AudioIO initialized and started successfully - microphone is ready");
                                        } catch (Exception e) {
                                            System.err.println("[CLIENT] ERROR: Failed to initialize AudioIO: " + e.getMessage());
                                            e.printStackTrace();
                                            audioRef[0] = null;
                                            // Thông báo lỗi nhưng vẫn tiếp tục (có thể retry sau)
                                            System.err.println("[CLIENT] WARNING: Mic button is ON but audio cannot be sent. Please check microphone permissions.");
                                            return;
                                        }
                                    } else {
                                        System.out.println("[CLIENT] AudioIO already exists, reusing for microphone");
                                        // Đảm bảo mic line đang chạy
                                        try {
                                            audioRef[0].startMic();
                                            // Đợi một chút để mic line khởi động hoàn toàn
                                            Thread.sleep(50);
                                        } catch (Exception e) {
                                            System.err.println("[CLIENT] Error starting mic line: " + e.getMessage());
                                            e.printStackTrace();
                                        }
                                    }
                                    
                                    // Bắt đầu loop gửi audio (chỉ một thread)
                                    Thread audioThread = new Thread(() -> {
                                        byte[] header = ByteBuffer.allocate(9).array();
                                        byte[] audioBuf = new byte[320]; // 10ms @ 16kHz mono 16-bit
                                        int packetCount = 0;
                                        
                                        System.out.println("[CLIENT] Audio sending thread started - mic is active");
                                        
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
                                                    
                                                    // Log mỗi 100 packet để xác nhận đang gửi
                                                    packetCount++;
                                                    if (packetCount % 100 == 0) {
                                                        System.out.println("[CLIENT] Audio packets sent: " + packetCount + " (mic is active)");
                                                    }
                                                } else {
                                                    try {
                                                        Thread.sleep(10); // 10ms
                                                    } catch (InterruptedException e) {
                                                        Thread.currentThread().interrupt();
                                                        break; // Thoát khi bị interrupt
                                                    }
                                                }
                                            } catch (Exception e) {
                                                if (e instanceof InterruptedException) {
                                                    Thread.currentThread().interrupt();
                                                    break; // Thoát khi bị interrupt
                                                }
                                                if (!Thread.currentThread().isInterrupted()) {
                                                    System.err.println("[CLIENT] Error sending audio: " + e.getMessage());
                                                    e.printStackTrace();
                                                }
                                                break;
                                            }
                                        }
                                        System.out.println("[CLIENT] Audio sending thread stopped (total packets: " + packetCount + ")");
                                    }, "AudioSender");
                                    audioThread.setDaemon(true);
                                    audioThread.start();
                                    audioSendingThreadRef.set(audioThread);
                                    System.out.println("[CLIENT] Mic is ON - audio sending thread created and started");
                                } else {
                                    // Tắt mic: dừng thread gửi audio và dừng mic line nhưng vẫn giữ speaker để nghe peers
                                    Thread oldThread = audioSendingThreadRef.getAndSet(null);
                                    if (oldThread != null && oldThread.isAlive()) {
                                        oldThread.interrupt();
                                        try {
                                            oldThread.join(200); // Đợi tối đa 200ms để thread dừng hoàn toàn
                                        } catch (InterruptedException e) {
                                            Thread.currentThread().interrupt();
                                        }
                                    }
                                    
                                    // Dừng mic line để đảm bảo không còn đọc từ mic
                                    if (audioRef[0] != null) {
                                        try {
                                            audioRef[0].stopMic();
                                            System.out.println("[CLIENT] Mic line stopped (speaker still active)");
                                        } catch (Exception e) {
                                            System.err.println("[CLIENT] Error stopping mic line: " + e.getMessage());
                                        }
                                    }
                                    System.out.println("[CLIENT] Mic turned off (speaker still active)");
                                }
                            } catch (Exception e) {
                                System.err.println("[CLIENT] Error toggling mic: " + e.getMessage());
                                e.printStackTrace();
                            }
                        });
                    }
                    
                    @Override 
                    public void onSendChat(String msg) {
                        // Ưu tiên dùng UDP (nhanh hơn, không block)
                        udpControl.sendChat(fRoom, msg);
                        // Fallback: vẫn gửi qua RMI nếu UDP fail (silent)
                        pool.execute(() -> {
                            try { 
                                svc.sendChat(fRoom, fSsrc, fName, msg); 
                            } catch (Exception e) {
                                // Ignore - UDP đã gửi rồi
                            }
                        });
                    }
                    
                    @Override
                    public void onSendPrivateChat(long toSsrc, String msg) {
                        // Ưu tiên dùng UDP (nhanh hơn, không block)
                        udpControl.sendPrivateChat(toSsrc, msg);
                        // Fallback: vẫn gửi qua RMI nếu UDP fail (silent)
                        pool.execute(() -> {
                            try {
                                svc.sendPrivateChat(fRoom, fSsrc, fName, toSsrc, msg);
                            } catch (Exception e) {
                                // Ignore - UDP đã gửi rồi
                            }
                        });
                    }
                    
                    @Override 
                    public void onClose() {
                        // Leave room - ưu tiên dùng TCP
                        if (tcpClient != null) {
                            tcpClient.leave(fSsrc);
                            tcpClient.close();
                        } else if (svc != null) {
                            try { 
                                svc.leave(fSsrc); 
                            } catch (Exception ignored) {}
                        }
                        udpControl.close();
                        keepalivePool.shutdownNow();
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

                // Video capture và gửi loop - giảm xuống 8fps (125ms) để giảm CPU load tối đa
                final boolean[] firstFrameSent = {false};
                final boolean[] wasCamOn = {false};
                final long[] lastFrameTime = {0};
                final java.util.concurrent.atomic.AtomicInteger pendingFrames = new java.util.concurrent.atomic.AtomicInteger(0);
                
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
                        
                        // Throttle frame rate - 8fps (125ms) và skip nếu có quá nhiều pending frames
                        long now = System.currentTimeMillis();
                        if (now - lastFrameTime[0] < 125) { // Tối thiểu 125ms giữa các frame (8fps)
                            return;
                        }
                        
                        // Skip frame nếu có quá nhiều pending (tránh lag)
                        if (pendingFrames.get() > 2) {
                            return;
                        }
                        
                        lastFrameTime[0] = now;
                        pendingFrames.incrementAndGet();
                        
                        // Capture và encode trong background thread để không block
                        pool.execute(() -> {
                            try {
                                byte[] jpeg = cam.captureJpeg();
                                if (jpeg == null || jpeg.length == 0) {
                                    pendingFrames.decrementAndGet();
                                    return;
                                }

                                // Hiển thị local (async, skip nếu có quá nhiều pending)
                                if (pendingFrames.get() <= 3) {
                                    final byte[] jpegCopy = jpeg.clone();
                                    SwingUtilities.invokeLater(() -> {
                                        try {
                                            BufferedImage img = ImageIO.read(new ByteArrayInputStream(jpegCopy));
                                            if (img != null) {
                                                ui.updateSelf(img);
                                            }
                                        } catch (Exception e) {
                                            // Ignore decode errors
                                        }
                                    });
                                }

                                // Gửi đến server (non-blocking)
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
                                    System.out.println("[CLIENT] Sending video to server " + serverRtpAddr + ", SSRC=" + fSsrc + ", frame size=" + jpeg.length + " bytes");
                                    firstFrameSent[0] = true;
                                }
                            } catch (Exception e) {
                                System.err.println("[CLIENT] Error sending video: " + e.getMessage());
                            } finally {
                                pendingFrames.decrementAndGet();
                            }
                        });
                    } catch (Exception e) {
                        System.err.println("[CLIENT] Error in video loop: " + e.getMessage());
                    }
                }, 0, 125, TimeUnit.MILLISECONDS); // 8fps (125ms interval) để giảm CPU load tối đa

                ui.setVisible(true);
            } catch (Exception e) {
                e.printStackTrace();
                JOptionPane.showMessageDialog(null, "Cannot start client: " + e, "Error",
                        JOptionPane.ERROR_MESSAGE);
            }
        });
    }
}
