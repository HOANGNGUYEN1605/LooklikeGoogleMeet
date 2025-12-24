package com.example.rtpav.server;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetSocketAddress;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * UDP Control Server - xử lý chat và peer updates qua UDP để giảm lag
 * Protocol:
 * - Packet format: [0: messageType (byte)] + [1-8: SSRC (long)] + [9-end: payload]
 * Message types:
 * - 0x01: CHAT - payload: [roomId length (int)] + [roomId bytes] + [fromName length (int)] + [fromName bytes] + [message bytes]
 * - 0x02: PRIVATE_CHAT - payload: [toSsrc (long)] + [fromName length (int)] + [fromName bytes] + [message bytes]
 * - 0x03: PEERS_UPDATE - payload: [roomId length (int)] + [roomId bytes] + [peer count (int)] + [peer data...]
 */
public class UdpControlServer implements Runnable {
    private final int udpControlPort;
    private final RoomManager rooms;
    private final Map<Long, InetSocketAddress> clientControlEndpoints = new ConcurrentHashMap<>(); // SSRC -> control endpoint
    private final String serverLANIP;
    
    public UdpControlServer(int udpControlPort, RoomManager rooms) {
        this.udpControlPort = udpControlPort;
        this.rooms = rooms;
        this.serverLANIP = findServerLANIP();
    }
    
    @Override
    public void run() {
        try (DatagramSocket sock = new DatagramSocket(udpControlPort)) {
            sock.setReceiveBufferSize(256 * 1024); // 256KB
            sock.setSendBufferSize(256 * 1024);
            sock.setReuseAddress(true);
            byte[] buf = new byte[64 * 1024];
            DatagramPacket pkt = new DatagramPacket(buf, buf.length);
            System.out.println("[SERVER] UDP Control Server listening on port " + udpControlPort);
            
            while (true) {
                sock.receive(pkt);
                int len = pkt.getLength();
                if (len < 9) continue;
                
                ByteBuffer bb = ByteBuffer.wrap(buf, 0, len);
                byte messageType = bb.get();
                long ssrc = bb.getLong();
                
                // Cập nhật control endpoint của client
                InetSocketAddress clientEp = (InetSocketAddress) pkt.getSocketAddress();
                String clientIP = clientEp.getAddress().getHostAddress();
                
                // Nếu client trên cùng máy server, dùng IP LAN
                if (clientEp.getAddress().isLoopbackAddress() || 
                    clientEp.getAddress().isAnyLocalAddress() ||
                    (serverLANIP != null && clientIP.equals(serverLANIP))) {
                    if (serverLANIP != null) {
                        clientEp = new InetSocketAddress(serverLANIP, clientEp.getPort());
                    }
                }
                clientControlEndpoints.put(ssrc, clientEp);
                
                try {
                    switch (messageType) {
                        case 0x01: // CHAT
                            handleChat(ssrc, bb, sock);
                            break;
                        case 0x02: // PRIVATE_CHAT
                            handlePrivateChat(ssrc, bb, sock);
                            break;
                        default:
                            // Unknown message type
                            break;
                    }
                } catch (Exception e) {
                    System.err.println("[SERVER] Error handling UDP control message: " + e.getMessage());
                }
            }
        } catch (Exception e) {
            System.err.println("[SERVER] UDP Control Server failed: " + e);
            e.printStackTrace();
        }
    }
    
    private void handleChat(long fromSsrc, ByteBuffer bb, DatagramSocket sock) {
        try {
            // Read roomId
            int roomIdLen = bb.getInt();
            byte[] roomIdBytes = new byte[roomIdLen];
            bb.get(roomIdBytes);
            String roomId = new String(roomIdBytes, StandardCharsets.UTF_8);
            
            // Read fromName
            int fromNameLen = bb.getInt();
            byte[] fromNameBytes = new byte[fromNameLen];
            bb.get(fromNameBytes);
            String fromName = new String(fromNameBytes, StandardCharsets.UTF_8);
            
            // Read message
            int msgLen = bb.remaining();
            byte[] msgBytes = new byte[msgLen];
            bb.get(msgBytes);
            String message = new String(msgBytes, StandardCharsets.UTF_8);
            
            // Format display name
            String displayName = fromName;
            if (fromName != null && !fromName.isEmpty()) {
                long shortId = fromSsrc % 1000;
                displayName = fromName + " #" + shortId;
            } else {
                displayName = "User #" + (fromSsrc % 1000);
            }
            
            // Broadcast to all clients in room
            int sent = 0;
            int failed = 0;
            for (var client : rooms.allClients()) {
                if (!client.getRoomId().equals(roomId) || client.getSsrc() == fromSsrc) {
                    continue;
                }
                
                InetSocketAddress targetEp = clientControlEndpoints.get(client.getSsrc());
                if (targetEp == null) {
                    // Fallback: use RTP endpoint if control endpoint not available
                    targetEp = client.getRtpEndpoint();
                    if (targetEp != null && targetEp.getAddress().isAnyLocalAddress()) {
                        if (serverLANIP != null) {
                            targetEp = new InetSocketAddress(serverLANIP, targetEp.getPort());
                        }
                    }
                }
                
                if (targetEp != null) {
                    try {
                        // Build response packet: [0: 0x01] + [1-8: fromSsrc] + [9-12: roomIdLen] + [roomId] + [fromNameLen] + [fromName] + [message]
                        ByteBuffer resp = ByteBuffer.allocate(9 + 4 + roomIdLen + 4 + fromNameLen + msgLen);
                        resp.put((byte) 0x01); // CHAT message
                        resp.putLong(fromSsrc);
                        resp.putInt(roomIdLen);
                        resp.put(roomIdBytes);
                        resp.putInt(fromNameLen);
                        resp.put(fromNameBytes);
                        resp.put(msgBytes);
                        
                        DatagramPacket outPkt = new DatagramPacket(resp.array(), resp.position(), targetEp);
                        sock.send(outPkt);
                        sent++;
                    } catch (Exception e) {
                        failed++;
                    }
                }
            }
            
            System.out.printf("[SERVER] UDP Chat: from SSRC=%d, to %d peer(s), failed=%d%n", fromSsrc, sent, failed);
        } catch (Exception e) {
            System.err.println("[SERVER] Error handling chat: " + e.getMessage());
        }
    }
    
    private void handlePrivateChat(long fromSsrc, ByteBuffer bb, DatagramSocket sock) {
        try {
            long toSsrc = bb.getLong();
            
            // Read fromName
            int fromNameLen = bb.getInt();
            byte[] fromNameBytes = new byte[fromNameLen];
            bb.get(fromNameBytes);
            String fromName = new String(fromNameBytes, StandardCharsets.UTF_8);
            
            // Read message
            int msgLen = bb.remaining();
            byte[] msgBytes = new byte[msgLen];
            bb.get(msgBytes);
            String message = new String(msgBytes, StandardCharsets.UTF_8);
            
            // Format display name
            String displayName = fromName;
            if (fromName != null && !fromName.isEmpty()) {
                long shortId = fromSsrc % 1000;
                displayName = fromName + " #" + shortId;
            } else {
                displayName = "User #" + (fromSsrc % 1000);
            }
            
            // Send to target client
            InetSocketAddress targetEp = clientControlEndpoints.get(toSsrc);
            if (targetEp == null) {
                var client = rooms.bySsrc(toSsrc);
                if (client != null) {
                    targetEp = client.getRtpEndpoint();
                    if (targetEp != null && targetEp.getAddress().isAnyLocalAddress()) {
                        if (serverLANIP != null) {
                            targetEp = new InetSocketAddress(serverLANIP, targetEp.getPort());
                        }
                    }
                }
            }
            
            if (targetEp != null) {
                try {
                    // Build response packet: [0: 0x02] + [1-8: fromSsrc] + [9-16: toSsrc] + [17-20: fromNameLen] + [fromName] + [message]
                    ByteBuffer resp = ByteBuffer.allocate(9 + 8 + 4 + fromNameLen + msgLen);
                    resp.put((byte) 0x02); // PRIVATE_CHAT message
                    resp.putLong(fromSsrc);
                    resp.putLong(toSsrc);
                    resp.putInt(fromNameLen);
                    resp.put(fromNameBytes);
                    resp.put(msgBytes);
                    
                    DatagramPacket outPkt = new DatagramPacket(resp.array(), resp.position(), targetEp);
                    sock.send(outPkt);
                    System.out.printf("[SERVER] UDP Private Chat sent: from SSRC=%d to SSRC=%d%n", fromSsrc, toSsrc);
                } catch (Exception e) {
                    System.err.println("[SERVER] Error sending private chat: " + e.getMessage());
                }
            }
        } catch (Exception e) {
            System.err.println("[SERVER] Error handling private chat: " + e.getMessage());
        }
    }
    
    /**
     * Broadcast peer updates to all clients in a room via UDP
     */
    public void broadcastPeersUpdate(String roomId, java.util.Collection<com.example.rtpav.server.ClientInfo> peers) {
        try (DatagramSocket sock = new DatagramSocket()) {
            sock.setSendBufferSize(256 * 1024);
            
            // Build peer list data
            java.util.List<byte[]> peerDataList = new java.util.ArrayList<>();
            int peerCount = 0;
            
            for (var peer : peers) {
                if (!peer.getRoomId().equals(roomId)) continue;
                peerCount++;
                
                InetSocketAddress ep = peer.getRtpEndpoint();
                String epIP = ep != null ? ep.getAddress().getHostAddress() : "0.0.0.0";
                int epPort = ep != null ? ep.getPort() : 0;
                
                // Fix IP if needed
                if (ep != null && (ep.getAddress().isAnyLocalAddress() || ep.getAddress().isLoopbackAddress() ||
                    (serverLANIP != null && epIP.equals(serverLANIP)))) {
                    if (serverLANIP != null) {
                        epIP = serverLANIP;
                    }
                }
                
                String name = peer.getName() != null ? peer.getName() : "";
                byte[] nameBytes = name.getBytes(StandardCharsets.UTF_8);
                byte[] ipBytes = epIP.getBytes(StandardCharsets.UTF_8);
                
                // Peer data: [ssrc (8)] + [nameLen (4)] + [name] + [ipLen (4)] + [ip] + [port (4)]
                ByteBuffer peerData = ByteBuffer.allocate(8 + 4 + nameBytes.length + 4 + ipBytes.length + 4);
                peerData.putLong(peer.getSsrc());
                peerData.putInt(nameBytes.length);
                peerData.put(nameBytes);
                peerData.putInt(ipBytes.length);
                peerData.put(ipBytes);
                peerData.putInt(epPort);
                
                peerDataList.add(peerData.array());
            }
            
            // Calculate total size
            byte[] roomIdBytes = roomId.getBytes(StandardCharsets.UTF_8);
            int totalSize = 0;
            for (byte[] peerData : peerDataList) {
                totalSize += peerData.length;
            }
            
            // Build packet: [0: 0x03] + [1-8: reserved] + [9-12: roomIdLen] + [roomId] + [13-16: peerCount] + [peer data...]
            ByteBuffer packet = ByteBuffer.allocate(9 + 4 + roomIdBytes.length + 4 + totalSize);
            packet.put((byte) 0x03); // PEERS_UPDATE
            packet.putLong(0); // reserved
            packet.putInt(roomIdBytes.length);
            packet.put(roomIdBytes);
            packet.putInt(peerDataList.size());
            for (byte[] peerData : peerDataList) {
                packet.put(peerData);
            }
            
            // Send to all clients in room
            int sent = 0;
            for (var client : rooms.allClients()) {
                if (!client.getRoomId().equals(roomId)) continue;
                
                InetSocketAddress targetEp = clientControlEndpoints.get(client.getSsrc());
                if (targetEp == null) {
                    targetEp = client.getRtpEndpoint();
                    if (targetEp != null && targetEp.getAddress().isAnyLocalAddress()) {
                        if (serverLANIP != null) {
                            targetEp = new InetSocketAddress(serverLANIP, targetEp.getPort());
                        }
                    }
                }
                
                if (targetEp != null) {
                    try {
                        DatagramPacket outPkt = new DatagramPacket(packet.array(), packet.position(), targetEp);
                        sock.send(outPkt);
                        sent++;
                    } catch (Exception e) {
                        // Ignore
                    }
                }
            }
            
            if (sent > 0) {
                System.out.printf("[SERVER] UDP Peers Update: room=%s, peers=%d, sent to %d client(s)%n", 
                    roomId, peerDataList.size(), sent);
            }
        } catch (Exception e) {
            System.err.println("[SERVER] Error broadcasting peers update: " + e.getMessage());
        }
    }
    
    public void removeClient(long ssrc) {
        clientControlEndpoints.remove(ssrc);
    }
    
    private static String findServerLANIP() {
        try {
            java.util.Enumeration<java.net.NetworkInterface> interfaces = java.net.NetworkInterface.getNetworkInterfaces();
            while (interfaces.hasMoreElements()) {
                java.net.NetworkInterface ni = interfaces.nextElement();
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
                        if (ip.startsWith("192.168.56.") || ip.startsWith("172.24.") || 
                            ip.startsWith("172.30.") || ip.startsWith("169.254.")) {
                            continue;
                        }
                        if (ip.startsWith("192.168.") || ip.startsWith("10.") || 
                            (ip.startsWith("172.") && !ip.startsWith("172.24.") && !ip.startsWith("172.30."))) {
                            return ip;
                        }
                    }
                }
            }
        } catch (Exception e) {
            // Ignore
        }
        return null;
    }
}

