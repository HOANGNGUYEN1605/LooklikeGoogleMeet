package com.example.rtpav.client;

import com.example.rtpav.rmi.PeerInfo;

import java.io.*;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

/**
 * TCP Control Client - xử lý login, join, leave qua TCP để đảm bảo và giảm lag
 */
public class TcpControlClient {
    private final String serverHost;
    private final int serverPort;
    private AtomicReference<Socket> socketRef = new AtomicReference<>();
    private DataInputStream in;
    private DataOutputStream out;
    private volatile boolean connected = false;
    
    public TcpControlClient(String serverHost, int serverPort) {
        this.serverHost = serverHost;
        this.serverPort = serverPort;
    }
    
    /**
     * Connect to server
     */
    public boolean connect() {
        try {
            Socket socket = new Socket(serverHost, serverPort);
            socket.setTcpNoDelay(true); // Disable Nagle algorithm
            socket.setKeepAlive(true);
            socket.setSoTimeout(30000); // 30s timeout
            socket.setReceiveBufferSize(64 * 1024);
            socket.setSendBufferSize(64 * 1024);
            
            in = new DataInputStream(new BufferedInputStream(socket.getInputStream()));
            out = new DataOutputStream(new BufferedOutputStream(socket.getOutputStream()));
            
            socketRef.set(socket);
            connected = true;
            System.out.println("[CLIENT] TCP Control connected to " + serverHost + ":" + serverPort);
            return true;
        } catch (Exception e) {
            System.err.println("[CLIENT] Error connecting TCP: " + e.getMessage());
            connected = false;
            return false;
        }
    }
    
    /**
     * Login
     */
    public String login(String username, String password) {
        if (!connected || socketRef.get() == null) {
            if (!connect()) return null;
        }
        
        try {
            byte[] usernameBytes = username.getBytes(StandardCharsets.UTF_8);
            byte[] passwordBytes = password.getBytes(StandardCharsets.UTF_8);
            
            ByteBuffer payload = ByteBuffer.allocate(4 + usernameBytes.length + 4 + passwordBytes.length);
            payload.putInt(usernameBytes.length);
            payload.put(usernameBytes);
            payload.putInt(passwordBytes.length);
            payload.put(passwordBytes);
            
            // Send message
            out.writeInt(0x01); // LOGIN
            out.writeInt(payload.position());
            out.write(payload.array(), 0, payload.position());
            out.flush();
            
            // Read response
            byte success = in.readByte();
            int displayNameLen = in.readInt();
            
            if (success == 1 && displayNameLen > 0) {
                byte[] displayNameBytes = new byte[displayNameLen];
                in.readFully(displayNameBytes);
                String displayName = new String(displayNameBytes, StandardCharsets.UTF_8);
                System.out.println("[CLIENT] TCP Login successful: " + displayName);
                return displayName;
            } else {
                System.out.println("[CLIENT] TCP Login failed");
                return null;
            }
        } catch (Exception e) {
            System.err.println("[CLIENT] Error during TCP login: " + e.getMessage());
            connected = false;
            return null;
        }
    }
    
    /**
     * Register
     */
    public String register(String username, String password, String displayName, String email) {
        if (!connected || socketRef.get() == null) {
            if (!connect()) return null;
        }
        
        try {
            byte[] usernameBytes = username.getBytes(StandardCharsets.UTF_8);
            byte[] passwordBytes = password.getBytes(StandardCharsets.UTF_8);
            byte[] displayNameBytes = displayName.getBytes(StandardCharsets.UTF_8);
            byte[] emailBytes = email.getBytes(StandardCharsets.UTF_8);
            
            ByteBuffer payload = ByteBuffer.allocate(4 + usernameBytes.length + 4 + passwordBytes.length + 
                                                     4 + displayNameBytes.length + 4 + emailBytes.length);
            payload.putInt(usernameBytes.length);
            payload.put(usernameBytes);
            payload.putInt(passwordBytes.length);
            payload.put(passwordBytes);
            payload.putInt(displayNameBytes.length);
            payload.put(displayNameBytes);
            payload.putInt(emailBytes.length);
            payload.put(emailBytes);
            
            // Send message
            out.writeInt(0x02); // REGISTER
            out.writeInt(payload.position());
            out.write(payload.array(), 0, payload.position());
            out.flush();
            
            // Read response
            byte success = in.readByte();
            int displayNameLen = in.readInt();
            
            if (success == 1 && displayNameLen > 0) {
                byte[] resultDisplayNameBytes = new byte[displayNameLen];
                in.readFully(resultDisplayNameBytes);
                String resultDisplayName = new String(resultDisplayNameBytes, StandardCharsets.UTF_8);
                System.out.println("[CLIENT] TCP Register successful: " + resultDisplayName);
                return resultDisplayName;
            } else {
                System.out.println("[CLIENT] TCP Register failed");
                return null;
            }
        } catch (Exception e) {
            System.err.println("[CLIENT] Error during TCP register: " + e.getMessage());
            connected = false;
            return null;
        }
    }
    
    /**
     * Join room
     */
    public Set<PeerInfo> join(String roomId, long ssrc, String name, int rtpPort) {
        if (!connected || socketRef.get() == null) {
            if (!connect()) return new HashSet<>();
        }
        
        try {
            byte[] roomIdBytes = roomId.getBytes(StandardCharsets.UTF_8);
            byte[] nameBytes = name.getBytes(StandardCharsets.UTF_8);
            
            ByteBuffer payload = ByteBuffer.allocate(4 + roomIdBytes.length + 8 + 4 + nameBytes.length + 4);
            payload.putInt(roomIdBytes.length);
            payload.put(roomIdBytes);
            payload.putLong(ssrc);
            payload.putInt(nameBytes.length);
            payload.put(nameBytes);
            payload.putInt(rtpPort);
            
            // Send message
            out.writeInt(0x03); // JOIN
            out.writeInt(payload.position());
            out.write(payload.array(), 0, payload.position());
            out.flush();
            
            // Read response
            byte success = in.readByte();
            if (success != 1) {
                System.err.println("[CLIENT] TCP Join failed");
                return new HashSet<>();
            }
            
            int peerCount = in.readInt();
            Set<PeerInfo> peers = new HashSet<>();
            
            for (int i = 0; i < peerCount; i++) {
                long peerSsrc = in.readLong();
                
                int peerNameLen = in.readInt();
                byte[] peerNameBytes = new byte[peerNameLen];
                in.readFully(peerNameBytes);
                String peerName = new String(peerNameBytes, StandardCharsets.UTF_8);
                
                int peerIPLen = in.readInt();
                byte[] peerIPBytes = new byte[peerIPLen];
                in.readFully(peerIPBytes);
                String peerIP = new String(peerIPBytes, StandardCharsets.UTF_8);
                
                int peerPort = in.readInt();
                
                java.net.InetSocketAddress endpoint = new java.net.InetSocketAddress(peerIP, peerPort);
                peers.add(new PeerInfo(peerSsrc, roomId, peerName, endpoint));
            }
            
            System.out.println("[CLIENT] TCP Join successful: " + peerCount + " peers");
            return peers;
        } catch (Exception e) {
            System.err.println("[CLIENT] Error during TCP join: " + e.getMessage());
            connected = false;
            return new HashSet<>();
        }
    }
    
    /**
     * Leave room
     */
    public boolean leave(long ssrc) {
        if (!connected || socketRef.get() == null) {
            return false;
        }
        
        try {
            ByteBuffer payload = ByteBuffer.allocate(8);
            payload.putLong(ssrc);
            
            // Send message
            out.writeInt(0x04); // LEAVE
            out.writeInt(8);
            out.write(payload.array());
            out.flush();
            
            // Read response
            byte success = in.readByte();
            System.out.println("[CLIENT] TCP Leave: " + (success == 1 ? "success" : "failed"));
            return success == 1;
        } catch (Exception e) {
            System.err.println("[CLIENT] Error during TCP leave: " + e.getMessage());
            connected = false;
            return false;
        }
    }
    
    /**
     * Get peers
     */
    public Set<PeerInfo> getPeers(String roomId) {
        if (!connected || socketRef.get() == null) {
            if (!connect()) return new HashSet<>();
        }
        
        try {
            byte[] roomIdBytes = roomId.getBytes(StandardCharsets.UTF_8);
            
            ByteBuffer payload = ByteBuffer.allocate(4 + roomIdBytes.length);
            payload.putInt(roomIdBytes.length);
            payload.put(roomIdBytes);
            
            // Send message
            out.writeInt(0x05); // GET_PEERS
            out.writeInt(payload.position());
            out.write(payload.array(), 0, payload.position());
            out.flush();
            
            // Read response
            int peerCount = in.readInt();
            Set<PeerInfo> peers = new HashSet<>();
            
            for (int i = 0; i < peerCount; i++) {
                long peerSsrc = in.readLong();
                
                int peerNameLen = in.readInt();
                byte[] peerNameBytes = new byte[peerNameLen];
                in.readFully(peerNameBytes);
                String peerName = new String(peerNameBytes, StandardCharsets.UTF_8);
                
                int peerIPLen = in.readInt();
                byte[] peerIPBytes = new byte[peerIPLen];
                in.readFully(peerIPBytes);
                String peerIP = new String(peerIPBytes, StandardCharsets.UTF_8);
                
                int peerPort = in.readInt();
                
                java.net.InetSocketAddress endpoint = new java.net.InetSocketAddress(peerIP, peerPort);
                peers.add(new PeerInfo(peerSsrc, roomId, peerName, endpoint));
            }
            
            return peers;
        } catch (Exception e) {
            System.err.println("[CLIENT] Error during TCP getPeers: " + e.getMessage());
            connected = false;
            return new HashSet<>();
        }
    }
    
    public void close() {
        connected = false;
        Socket socket = socketRef.getAndSet(null);
        if (socket != null) {
            try {
                socket.close();
            } catch (Exception e) {
                // Ignore
            }
        }
    }
}

