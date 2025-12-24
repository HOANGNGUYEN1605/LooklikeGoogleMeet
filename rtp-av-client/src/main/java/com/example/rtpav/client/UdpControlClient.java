package com.example.rtpav.client;

import com.example.rtpav.rmi.PeerInfo;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetSocketAddress;
import java.util.HashSet;
import java.util.Set;

/**
 * UDP Control Client - nhận chat và peer updates qua UDP để giảm lag
 */
public class UdpControlClient implements Runnable {
    private final DatagramSocket socket;
    private final InetSocketAddress serverAddr;
    private final long mySsrc;
    private final String myName;
    private final ClientUI ui;
    private final int controlPort;
    private volatile boolean running = true;
    
    public UdpControlClient(int controlPort, InetSocketAddress serverAddr, long mySsrc, String myName, ClientUI ui) throws Exception {
        this.controlPort = controlPort;
        this.serverAddr = serverAddr;
        this.mySsrc = mySsrc;
        this.myName = myName;
        this.ui = ui;
        this.socket = new DatagramSocket(controlPort);
        socket.setReceiveBufferSize(256 * 1024);
        socket.setSendBufferSize(256 * 1024);
        socket.setReuseAddress(true);
    }
    
    @Override
    public void run() {
        byte[] buf = new byte[64 * 1024];
        DatagramPacket pkt = new DatagramPacket(buf, buf.length);
        System.out.println("[CLIENT] UDP Control Client listening on port " + controlPort);
        
        while (running && !Thread.currentThread().isInterrupted()) {
            try {
                socket.setSoTimeout(1000);
                socket.receive(pkt);
                int len = pkt.getLength();
                if (len < 9) continue;
                
                ByteBuffer bb = ByteBuffer.wrap(buf, 0, len);
                byte messageType = bb.get();
                
                // For chat and peers update, read SSRC to check
                if (messageType == 0x01 || messageType == 0x03) {
                    long fromSsrc = bb.getLong();
                    // Ignore messages from self (except peers update which includes self)
                    if (messageType == 0x01 && fromSsrc == mySsrc) continue;
                }
                
                switch (messageType) {
                    case 0x01: // CHAT
                        handleChat(bb);
                        break;
                    case 0x02: // PRIVATE_CHAT
                        handlePrivateChat(bb);
                        break;
                    case 0x03: // PEERS_UPDATE
                        handlePeersUpdate(bb);
                        break;
                }
            } catch (java.net.SocketTimeoutException e) {
                // Normal, continue
                continue;
            } catch (Exception e) {
                if (running) {
                    System.err.println("[CLIENT] Error receiving UDP control: " + e.getMessage());
                }
            }
        }
    }
    
    private void handleChat(ByteBuffer bb) {
        try {
            // Skip roomId (not needed for display)
            int roomIdLen = bb.getInt();
            bb.position(bb.position() + roomIdLen);
            
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
            
            // Update UI
            javax.swing.SwingUtilities.invokeLater(() -> {
                ui.addChat(fromName, message);
            });
        } catch (Exception e) {
            System.err.println("[CLIENT] Error handling chat: " + e.getMessage());
        }
    }
    
    private void handlePrivateChat(ByteBuffer bb) {
        try {
            long fromSsrc = bb.getLong(); // fromSsrc is first after messageType
            long toSsrc = bb.getLong();
            if (toSsrc != mySsrc) return; // Not for us
            
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
            
            // Update UI
            final long finalFromSsrc = fromSsrc;
            javax.swing.SwingUtilities.invokeLater(() -> {
                ui.addPrivateChat(finalFromSsrc, fromName, message);
            });
        } catch (Exception e) {
            System.err.println("[CLIENT] Error handling private chat: " + e.getMessage());
        }
    }
    
    private void handlePeersUpdate(ByteBuffer bb) {
        try {
            // Read roomId
            int roomIdLen = bb.getInt();
            byte[] roomIdBytes = new byte[roomIdLen];
            bb.get(roomIdBytes);
            String roomId = new String(roomIdBytes, StandardCharsets.UTF_8);
            
            // Read peer count
            int peerCount = bb.getInt();
            Set<PeerInfo> peers = new HashSet<>();
            
            for (int i = 0; i < peerCount; i++) {
                long ssrc = bb.getLong();
                
                // Read name
                int nameLen = bb.getInt();
                byte[] nameBytes = new byte[nameLen];
                bb.get(nameBytes);
                String name = new String(nameBytes, StandardCharsets.UTF_8);
                
                // Read IP
                int ipLen = bb.getInt();
                byte[] ipBytes = new byte[ipLen];
                bb.get(ipBytes);
                String ip = new String(ipBytes, StandardCharsets.UTF_8);
                
                // Read port
                int port = bb.getInt();
                
                InetSocketAddress endpoint = new InetSocketAddress(ip, port);
                peers.add(new PeerInfo(ssrc, roomId, name, endpoint));
            }
            
            // Update UI
            javax.swing.SwingUtilities.invokeLater(() -> {
                ui.setPeers(peers);
                ui.setLastPeerCount(peers.size());
            });
            
            System.out.println("[CLIENT] UDP Peers Update received: " + peers.size() + " peers");
        } catch (Exception e) {
            System.err.println("[CLIENT] Error handling peers update: " + e.getMessage());
        }
    }
    
    /**
     * Send chat message via UDP
     */
    public void sendChat(String roomId, String message) {
        try {
            byte[] roomIdBytes = roomId.getBytes(StandardCharsets.UTF_8);
            byte[] nameBytes = myName.getBytes(StandardCharsets.UTF_8);
            byte[] msgBytes = message.getBytes(StandardCharsets.UTF_8);
            
            ByteBuffer packet = ByteBuffer.allocate(9 + 4 + roomIdBytes.length + 4 + nameBytes.length + msgBytes.length);
            packet.put((byte) 0x01); // CHAT
            packet.putLong(mySsrc);
            packet.putInt(roomIdBytes.length);
            packet.put(roomIdBytes);
            packet.putInt(nameBytes.length);
            packet.put(nameBytes);
            packet.put(msgBytes);
            
            DatagramPacket pkt = new DatagramPacket(packet.array(), packet.position(), serverAddr);
            socket.send(pkt);
        } catch (Exception e) {
            System.err.println("[CLIENT] Error sending UDP chat: " + e.getMessage());
        }
    }
    
    /**
     * Send private chat message via UDP
     */
    public void sendPrivateChat(long toSsrc, String message) {
        try {
            byte[] nameBytes = myName.getBytes(StandardCharsets.UTF_8);
            byte[] msgBytes = message.getBytes(StandardCharsets.UTF_8);
            
            ByteBuffer packet = ByteBuffer.allocate(9 + 8 + 4 + nameBytes.length + msgBytes.length);
            packet.put((byte) 0x02); // PRIVATE_CHAT
            packet.putLong(mySsrc);
            packet.putLong(toSsrc);
            packet.putInt(nameBytes.length);
            packet.put(nameBytes);
            packet.put(msgBytes);
            
            DatagramPacket pkt = new DatagramPacket(packet.array(), packet.position(), serverAddr);
            socket.send(pkt);
        } catch (Exception e) {
            System.err.println("[CLIENT] Error sending UDP private chat: " + e.getMessage());
        }
    }
    
    /**
     * Register control endpoint with server (send a keepalive)
     */
    public void registerEndpoint() {
        try {
            // Send registration packet: [0: 0x00] + [1-8: SSRC]
            ByteBuffer packet = ByteBuffer.allocate(9);
            packet.put((byte) 0x00); // REGISTER
            packet.putLong(mySsrc);
            
            DatagramPacket pkt = new DatagramPacket(packet.array(), 9, serverAddr);
            socket.send(pkt);
        } catch (Exception e) {
            System.err.println("[CLIENT] Error registering control endpoint: " + e.getMessage());
        }
    }
    
    public void close() {
        running = false;
        if (socket != null && !socket.isClosed()) {
            socket.close();
        }
    }
}

