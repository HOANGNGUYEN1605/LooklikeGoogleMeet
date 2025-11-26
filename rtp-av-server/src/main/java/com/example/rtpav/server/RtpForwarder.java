package com.example.rtpav.server;

import java.nio.ByteBuffer;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetSocketAddress;

/** Nhận gói RTP từ 1 client và forward cho các client cùng phòng. Đồng thời cập nhật endpoint lên Dashboard. */
public class RtpForwarder implements Runnable {
    private final int udpPort;
    private final RoomManager rooms;

    public RtpForwarder(int udpPort, RoomManager rooms) {
        this.udpPort = udpPort;
        this.rooms = rooms;
    }

    @Override public void run() {
        try (DatagramSocket sock = new DatagramSocket(udpPort)) {
            sock.setReceiveBufferSize(1 << 20);
            sock.setSendBufferSize(1 << 20);
            byte[] buf = new byte[64 * 1024]; // Đủ lớn cho JPEG frames
            DatagramPacket pkt = new DatagramPacket(buf, buf.length);
            System.out.println("[SERVER] RTP forwarder on UDP " + udpPort);
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
                        // Đảm bảo forward đến địa chỉ thật (không phải 0.0.0.0)
                        InetSocketAddress targetAddr = dst;
                        if (dst.getAddress().isAnyLocalAddress()) {
                            // Nếu endpoint là 0.0.0.0, thử dùng localhost
                            targetAddr = new InetSocketAddress("127.0.0.1", dst.getPort());
                        }
                        
                        DatagramPacket out = new DatagramPacket(buf, len, targetAddr);
                        sock.send(out);
                        forwarded++;
                    } catch (Exception e) {
                        System.err.println("[SERVER] Failed to forward to " + dst + ": " + e.getMessage());
                    }
                }
                
                if (mediaType == 0) {
                    if (forwarded > 0) {
                        System.out.println("[SERVER] Forwarded video from SSRC=" + ssrc + " to " + forwarded + " peer(s)");
                    } else if (skipped > 0) {
                        System.out.println("[SERVER] Video from SSRC=" + ssrc + " waiting for " + skipped + " peer(s) to send first packet");
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("[SERVER] RTP forwarder failed: " + e);
        }
    }
}
