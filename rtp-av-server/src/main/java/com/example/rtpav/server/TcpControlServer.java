package com.example.rtpav.server;

import com.example.rtpav.rmi.PeerInfo;
import com.example.rtpav.server.auth.AuthService;
import com.example.rtpav.server.database.User;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;

/**
 * TCP Control Server - xử lý login, join, leave qua TCP để đảm bảo và giảm lag
 * Protocol:
 * - Message format: [0-3: messageType (int)] + [4-7: payloadLength (int)] + [payload]
 * Message types:
 * - 0x01: LOGIN - payload: [usernameLen (int)] + [username] + [passwordLen (int)] + [password]
 * - 0x02: REGISTER - payload: [usernameLen] + [username] + [passwordLen] + [password] + [displayNameLen] + [displayName] + [emailLen] + [email]
 * - 0x03: JOIN - payload: [roomIdLen] + [roomId] + [ssrc (long)] + [nameLen] + [name] + [rtpPort (int)]
 * - 0x04: LEAVE - payload: [ssrc (long)]
 * - 0x05: GET_PEERS - payload: [roomIdLen] + [roomId]
 */
public class TcpControlServer implements Runnable {
    private final int tcpPort;
    private final RoomManager rooms;
    private final AuthService authService;
    private final UdpControlServer udpControlServer;
    private final Map<Long, Socket> clientSockets = new ConcurrentHashMap<>(); // SSRC -> Socket
    private final ExecutorService executor = Executors.newCachedThreadPool();
    private volatile boolean running = true;
    
    public TcpControlServer(int tcpPort, RoomManager rooms, UdpControlServer udpControlServer) {
        this.tcpPort = tcpPort;
        this.rooms = rooms;
        this.authService = new AuthService();
        this.udpControlServer = udpControlServer;
    }
    
    @Override
    public void run() {
        try (ServerSocket serverSocket = new ServerSocket(tcpPort)) {
            serverSocket.setReuseAddress(true);
            System.out.println("[SERVER] TCP Control Server listening on port " + tcpPort);
            
            while (running) {
                try {
                    Socket clientSocket = serverSocket.accept();
                    // Tối ưu TCP settings
                    clientSocket.setTcpNoDelay(true); // Disable Nagle algorithm
                    clientSocket.setKeepAlive(true);
                    clientSocket.setSoTimeout(30000); // 30s timeout
                    clientSocket.setReceiveBufferSize(64 * 1024);
                    clientSocket.setSendBufferSize(64 * 1024);
                    
                    executor.submit(() -> handleClient(clientSocket));
                } catch (Exception e) {
                    if (running) {
                        System.err.println("[SERVER] Error accepting TCP connection: " + e.getMessage());
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("[SERVER] TCP Control Server failed: " + e);
            e.printStackTrace();
        }
    }
    
    private void handleClient(Socket socket) {
        try (DataInputStream in = new DataInputStream(new BufferedInputStream(socket.getInputStream()));
             DataOutputStream out = new DataOutputStream(new BufferedOutputStream(socket.getOutputStream()))) {
            
            while (running && !socket.isClosed()) {
                try {
                    int messageType = in.readInt();
                    int payloadLength = in.readInt();
                    
                    if (payloadLength < 0 || payloadLength > 64 * 1024) {
                        System.err.println("[SERVER] Invalid payload length: " + payloadLength);
                        break;
                    }
                    
                    byte[] payload = new byte[payloadLength];
                    in.readFully(payload);
                    
                    ByteBuffer bb = ByteBuffer.wrap(payload);
                    
                    switch (messageType) {
                        case 0x01: // LOGIN
                            handleLogin(bb, out);
                            break;
                        case 0x02: // REGISTER
                            handleRegister(bb, out);
                            break;
                        case 0x03: // JOIN
                            handleJoin(bb, out, socket);
                            break;
                        case 0x04: // LEAVE
                            handleLeave(bb, out);
                            break;
                        case 0x05: // GET_PEERS
                            handleGetPeers(bb, out);
                            break;
                        default:
                            System.err.println("[SERVER] Unknown message type: " + messageType);
                            break;
                    }
                } catch (EOFException e) {
                    // Client closed connection
                    break;
                } catch (Exception e) {
                    System.err.println("[SERVER] Error handling TCP message: " + e.getMessage());
                    break;
                }
            }
        } catch (Exception e) {
            System.err.println("[SERVER] Error in TCP client handler: " + e.getMessage());
        } finally {
            try {
                socket.close();
            } catch (Exception e) {
                // Ignore
            }
        }
    }
    
    private void handleLogin(ByteBuffer bb, DataOutputStream out) throws IOException {
        // Read username
        int usernameLen = bb.getInt();
        byte[] usernameBytes = new byte[usernameLen];
        bb.get(usernameBytes);
        String username = new String(usernameBytes, StandardCharsets.UTF_8);
        
        // Read password
        int passwordLen = bb.getInt();
        byte[] passwordBytes = new byte[passwordLen];
        bb.get(passwordBytes);
        String password = new String(passwordBytes, StandardCharsets.UTF_8);
        
        // Authenticate
        User user = authService.login(username, password);
        
        // Send response: [success (byte)] + [displayNameLen (int)] + [displayName]
        if (user != null) {
            byte[] displayNameBytes = user.getDisplayName().getBytes(StandardCharsets.UTF_8);
            out.writeByte(1); // success
            out.writeInt(displayNameBytes.length);
            out.write(displayNameBytes);
            System.out.println("[SERVER] TCP Login successful: " + username);
        } else {
            out.writeByte(0); // failure
            out.writeInt(0);
            System.out.println("[SERVER] TCP Login failed: " + username);
        }
        out.flush();
    }
    
    private void handleRegister(ByteBuffer bb, DataOutputStream out) throws IOException {
        // Read username
        int usernameLen = bb.getInt();
        byte[] usernameBytes = new byte[usernameLen];
        bb.get(usernameBytes);
        String username = new String(usernameBytes, StandardCharsets.UTF_8);
        
        // Read password
        int passwordLen = bb.getInt();
        byte[] passwordBytes = new byte[passwordLen];
        bb.get(passwordBytes);
        String password = new String(passwordBytes, StandardCharsets.UTF_8);
        
        // Read displayName
        int displayNameLen = bb.getInt();
        byte[] displayNameBytes = new byte[displayNameLen];
        bb.get(displayNameBytes);
        String displayName = new String(displayNameBytes, StandardCharsets.UTF_8);
        
        // Read email
        int emailLen = bb.getInt();
        byte[] emailBytes = new byte[emailLen];
        bb.get(emailBytes);
        String email = new String(emailBytes, StandardCharsets.UTF_8);
        
        // Register
        User user = authService.register(username, password, displayName, email);
        
        // Send response
        if (user != null) {
            byte[] resultDisplayNameBytes = user.getDisplayName().getBytes(StandardCharsets.UTF_8);
            out.writeByte(1); // success
            out.writeInt(resultDisplayNameBytes.length);
            out.write(resultDisplayNameBytes);
            System.out.println("[SERVER] TCP Register successful: " + username);
        } else {
            out.writeByte(0); // failure
            out.writeInt(0);
            System.out.println("[SERVER] TCP Register failed: " + username);
        }
        out.flush();
    }
    
    private void handleJoin(ByteBuffer bb, DataOutputStream out, Socket socket) throws IOException {
        // Read roomId
        int roomIdLen = bb.getInt();
        byte[] roomIdBytes = new byte[roomIdLen];
        bb.get(roomIdBytes);
        String roomId = new String(roomIdBytes, StandardCharsets.UTF_8);
        
        // Read SSRC
        long ssrc = bb.getLong();
        
        // Read name
        int nameLen = bb.getInt();
        byte[] nameBytes = new byte[nameLen];
        bb.get(nameBytes);
        String name = new String(nameBytes, StandardCharsets.UTF_8);
        
        // Read RTP port
        int rtpPort = bb.getInt();
        
        // Join room
        java.net.InetSocketAddress placeholder = new java.net.InetSocketAddress("0.0.0.0", rtpPort);
        rooms.join(roomId, ssrc, name != null ? name : "User", placeholder);
        
        // Store socket for this client
        clientSockets.put(ssrc, socket);
        
        // Broadcast peer update via UDP
        if (udpControlServer != null) {
            udpControlServer.broadcastPeersUpdate(roomId, rooms.allClients());
        }
        
        // Send response: [success (byte)] + [peerCount (int)] + [peer data...]
        Set<PeerInfo> peers = getPeers(roomId);
        out.writeByte(1); // success
        out.writeInt(peers.size());
        
        for (PeerInfo peer : peers) {
            byte[] peerNameBytes = peer.getName().getBytes(StandardCharsets.UTF_8);
            String peerIP = peer.getRtpEndpoint() != null ? peer.getRtpEndpoint().getAddress().getHostAddress() : "0.0.0.0";
            byte[] peerIPBytes = peerIP.getBytes(StandardCharsets.UTF_8);
            int peerPort = peer.getRtpEndpoint() != null ? peer.getRtpEndpoint().getPort() : 0;
            
            out.writeLong(peer.getSsrc());
            out.writeInt(peerNameBytes.length);
            out.write(peerNameBytes);
            out.writeInt(peerIPBytes.length);
            out.write(peerIPBytes);
            out.writeInt(peerPort);
        }
        out.flush();
        
        System.out.printf("[SERVER] TCP JOIN: ssrc=%d, name=%s, room=%s, peers=%d%n", ssrc, name, roomId, peers.size());
    }
    
    private void handleLeave(ByteBuffer bb, DataOutputStream out) throws IOException {
        long ssrc = bb.getLong();
        
        String room = rooms.leave(ssrc) != null ? rooms.bySsrc(ssrc) != null ? rooms.bySsrc(ssrc).getRoomId() : null : null;
        clientSockets.remove(ssrc);
        
        if (room != null && udpControlServer != null) {
            udpControlServer.broadcastPeersUpdate(room, rooms.allClients());
        }
        
        out.writeByte(1); // success
        out.flush();
        
        System.out.printf("[SERVER] TCP LEAVE: ssrc=%d%n", ssrc);
    }
    
    private void handleGetPeers(ByteBuffer bb, DataOutputStream out) throws IOException {
        // Read roomId
        int roomIdLen = bb.getInt();
        byte[] roomIdBytes = new byte[roomIdLen];
        bb.get(roomIdBytes);
        String roomId = new String(roomIdBytes, StandardCharsets.UTF_8);
        
        Set<PeerInfo> peers = getPeers(roomId);
        
        // Send response
        out.writeInt(peers.size());
        for (PeerInfo peer : peers) {
            byte[] peerNameBytes = peer.getName().getBytes(StandardCharsets.UTF_8);
            String peerIP = peer.getRtpEndpoint() != null ? peer.getRtpEndpoint().getAddress().getHostAddress() : "0.0.0.0";
            byte[] peerIPBytes = peerIP.getBytes(StandardCharsets.UTF_8);
            int peerPort = peer.getRtpEndpoint() != null ? peer.getRtpEndpoint().getPort() : 0;
            
            out.writeLong(peer.getSsrc());
            out.writeInt(peerNameBytes.length);
            out.write(peerNameBytes);
            out.writeInt(peerIPBytes.length);
            out.write(peerIPBytes);
            out.writeInt(peerPort);
        }
        out.flush();
    }
    
    private Set<PeerInfo> getPeers(String roomId) {
        String serverLANIP = findServerLANIP();
        return rooms.allClients().stream()
                .filter(ci -> roomId.equals(ci.getRoomId()))
                .map(ci -> {
                    java.net.InetSocketAddress ep = ci.getRtpEndpoint();
                    if (ep != null) {
                        String epIP = ep.getAddress().getHostAddress();
                        if (ep.getAddress().isAnyLocalAddress() || 
                            ep.getAddress().isLoopbackAddress() ||
                            (serverLANIP != null && epIP.equals(serverLANIP))) {
                            if (serverLANIP != null) {
                                ep = new java.net.InetSocketAddress(serverLANIP, ep.getPort());
                            }
                        }
                    }
                    return new PeerInfo(ci.getSsrc(), ci.getRoomId(), ci.getName(), ep);
                })
                .collect(java.util.stream.Collectors.toSet());
    }
    
    public void shutdown() {
        running = false;
        executor.shutdown();
        for (Socket socket : clientSockets.values()) {
            try {
                socket.close();
            } catch (Exception e) {
                // Ignore
            }
        }
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

