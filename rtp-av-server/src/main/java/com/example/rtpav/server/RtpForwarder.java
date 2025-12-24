package com.example.rtpav.server;

import java.nio.ByteBuffer;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetSocketAddress;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/** Nhận gói RTP từ 1 client và forward cho các client cùng phòng. Đồng thời cập nhật endpoint lên Dashboard. */
public class RtpForwarder implements Runnable {
    private final int udpPort;
    private final RoomManager rooms;
    private final String serverLANIP; // Cache IP LAN của server
    private final Set<Long> loggedSameMachineClients = ConcurrentHashMap.newKeySet(); // Track đã log chưa

    public RtpForwarder(int udpPort, RoomManager rooms) {
        this.udpPort = udpPort;
        this.rooms = rooms;
        this.serverLANIP = findServerLANIP();
        if (serverLANIP != null) {
            System.out.println("[SERVER] RTP Forwarder: Server LAN IP = " + serverLANIP);
        }
    }

    @Override public void run() {
        try (DatagramSocket sock = new DatagramSocket(udpPort)) {
            // Tối ưu buffer sizes để giảm lag
            sock.setReceiveBufferSize(2 << 20); // 2MB
            sock.setSendBufferSize(2 << 20); // 2MB
            // Tắt Nagle algorithm để giảm delay (UDP không cần nhưng set để chắc chắn)
            sock.setReuseAddress(true);
            byte[] buf = new byte[64 * 1024]; // Đủ lớn cho JPEG frames
            DatagramPacket pkt = new DatagramPacket(buf, buf.length);
            System.out.println("[SERVER] RTP forwarder on UDP " + udpPort + " (optimized buffers)");
            while (true) {
                sock.receive(pkt);
                int len = pkt.getLength();
                if (len < 9) continue;

                // Packet format: [0-7: SSRC (long)] + [8: mediaType (byte)] + [9-end: payload]
                ByteBuffer bb = ByteBuffer.wrap(buf, 0, len);
                long ssrc = bb.getLong();
                byte mediaType = bb.get();

                // Cập nhật endpoint thật của sender (địa chỉ public khi qua NAT)
                InetSocketAddress realEp = (InetSocketAddress) pkt.getSocketAddress();
                String sourceIP = realEp.getAddress().getHostAddress();
                
                // Nếu source IP là localhost (127.0.0.1) hoặc trùng với IP LAN của server
                // => Client đang chạy trên cùng máy server
                // Cần dùng IP LAN thực của server để client khác có thể kết nối
                if (realEp.getAddress().isLoopbackAddress() || 
                    realEp.getAddress().isAnyLocalAddress() ||
                    (serverLANIP != null && sourceIP.equals(serverLANIP))) {
                    if (serverLANIP != null) {
                        realEp = new InetSocketAddress(serverLANIP, realEp.getPort());
                        // Chỉ log một lần cho mỗi client (SSRC) để tránh spam log
                        if (loggedSameMachineClients.add(ssrc)) {
                            System.out.println("[SERVER] Client on same machine detected (SSRC=" + ssrc + ", source=" + sourceIP + "), using server LAN IP: " + serverLANIP);
                        }
                    }
                }
                
                rooms.updateEndpoint(ssrc, realEp);
                ServerDashboard.get().setClients(rooms.allClients());

                ClientInfo sender = rooms.bySsrc(ssrc);
                if (sender == null) {
                    System.out.println("[SERVER] Unknown sender SSRC: " + ssrc);
                    continue;
                }

                // Forward cho các client khác cùng phòng
                int forwarded = 0;
                int skipped = 0;
                for (ClientInfo ci : rooms.allClients()) {
                    if (!ci.getRoomId().equals(sender.getRoomId())) continue;
                    if (ci.getSsrc() == ssrc) continue;
                    
                    InetSocketAddress dst = ci.getRtpEndpoint();
                    if (dst == null) {
                        skipped++;
                        if (skipped == 1) {
                            System.out.println("[SERVER] Peer " + ci.getSsrc() + " has no RTP endpoint yet (waiting for first packet)");
                        }
                        continue;
                    }

                    try {
                        // Đảm bảo forward đến địa chỉ thật (không phải 0.0.0.0 hoặc localhost)
                        InetSocketAddress targetAddr = dst;
                        String dstIP = dst.getAddress().getHostAddress();
                        
                        // Nếu endpoint là 0.0.0.0, 127.0.0.1, hoặc trùng với IP LAN của server
                        // => Client đang chạy trên cùng máy server
                        // Dùng IP LAN thực của server để client khác có thể kết nối
                        if (dst.getAddress().isAnyLocalAddress() || 
                            dst.getAddress().isLoopbackAddress() ||
                            (serverLANIP != null && dstIP.equals(serverLANIP))) {
                            if (serverLANIP != null) {
                                targetAddr = new InetSocketAddress(serverLANIP, dst.getPort());
                                // Log lần đầu để debug
                                if (System.currentTimeMillis() % 5000 < 100) {
                                    System.out.println("[SERVER] Forwarding to client on same machine: " + dst + " -> " + targetAddr);
                                }
                            } else {
                                // Fallback: dùng localhost nếu không tìm thấy LAN IP
                                targetAddr = new InetSocketAddress("127.0.0.1", dst.getPort());
                            }
                        }
                        
                        DatagramPacket out = new DatagramPacket(buf, len, targetAddr);
                        sock.send(out);
                        forwarded++;
                    } catch (Exception e) {
                        System.err.println("[SERVER] Failed to forward to " + dst + ": " + e.getMessage());
                        e.printStackTrace();
                    }
                }
                
                // Giảm logging để giảm I/O overhead - chỉ log khi cần thiết
                if (mediaType == 0) {
                    if (forwarded > 0) {
                        // Chỉ log mỗi 5 giây để tránh spam
                        if (System.currentTimeMillis() % 5000 < 200) {
                            System.out.println("[SERVER] Forwarded video from SSRC=" + ssrc + " to " + forwarded + " peer(s)");
                        }
                    } else if (skipped > 0 && System.currentTimeMillis() % 10000 < 200) {
                        System.out.println("[SERVER] Video from SSRC=" + ssrc + " waiting for " + skipped + " peer(s) to send first packet");
                    }
                } else if (mediaType == 1) {
                    // Log audio forwarding mỗi 10 giây
                    if (forwarded > 0 && System.currentTimeMillis() % 10000 < 200) {
                        System.out.println("[SERVER] Forwarded audio from SSRC=" + ssrc + " to " + forwarded + " peer(s)");
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("[SERVER] RTP forwarder failed: " + e);
        }
    }
    
    /**
     * Tìm IP LAN thực của server (bỏ qua virtual networks)
     */
    private static String findServerLANIP() {
        try {
            java.util.Enumeration<java.net.NetworkInterface> interfaces = java.net.NetworkInterface.getNetworkInterfaces();
            while (interfaces.hasMoreElements()) {
                java.net.NetworkInterface ni = interfaces.nextElement();
                // Skip virtual interfaces
                if (ni.isLoopback() || !ni.isUp() || ni.isVirtual()) continue;
                String name = ni.getDisplayName().toLowerCase();
                if (name.contains("virtual") || name.contains("hyper-v") || name.contains("wsl") || 
                    name.contains("zerotier") || name.contains("vmware") || name.contains("virtualbox")) {
                    continue;
                }
                
                java.util.Enumeration<java.net.InetAddress> addresses = ni.getInetAddresses();
                while (addresses.hasMoreElements()) {
                    java.net.InetAddress addr = addresses.nextElement();
                    if (addr instanceof java.net.Inet4Address) {
                        String ip = addr.getHostAddress();
                        // Skip virtual network IPs
                        if (ip.startsWith("192.168.56.") || ip.startsWith("172.24.") || 
                            ip.startsWith("172.30.") || ip.startsWith("169.254.")) {
                            continue;
                        }
                        // Prefer 192.168.x.x or 10.x.x.x (common LAN IPs)
                        if (ip.startsWith("192.168.") || ip.startsWith("10.") || 
                            (ip.startsWith("172.") && !ip.startsWith("172.24.") && !ip.startsWith("172.30."))) {
                            return ip;
                        }
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("[SERVER] Error finding server LAN IP: " + e.getMessage());
        }
        return null;
    }
}
