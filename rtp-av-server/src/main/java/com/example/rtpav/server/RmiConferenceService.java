package com.example.rtpav.server;

import com.example.rtpav.rmi.ClientCallback;
import com.example.rtpav.rmi.ConferenceService;
import com.example.rtpav.rmi.PeerInfo;
import com.example.rtpav.server.auth.AuthService;
import com.example.rtpav.server.database.User;

import java.net.InetSocketAddress;
import java.rmi.RemoteException;
import java.rmi.server.UnicastRemoteObject;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

public class RmiConferenceService extends UnicastRemoteObject implements ConferenceService {

    private final RoomManager rooms;
    private final AuthService authService;
    private final String serverLANIP; // Cache IP LAN của server
    private UdpControlServer udpControlServer; // UDP control server cho chat và peer updates

    // mapping ssrc -> (room, callback)
    private final Map<Long, ClientCallback> callbacks = new ConcurrentHashMap<>();
    private final Map<Long, String>        ssrcToRoom = new ConcurrentHashMap<>();
    
    public void setUdpControlServer(UdpControlServer controlServer) {
        this.udpControlServer = controlServer;
    }

    public RmiConferenceService(RoomManager rooms, int exportPort) throws RemoteException {
        super(exportPort);
        this.rooms = rooms;
        this.authService = new AuthService();
        this.serverLANIP = findServerLANIP();
        if (serverLANIP != null) {
            System.out.println("[SERVER] RMI Service: Server LAN IP = " + serverLANIP);
        }
        
        // Test database connection
        if (authService.isDatabaseAvailable()) {
            System.out.println("[SERVER] Database connection successful");
        } else {
            System.err.println("[SERVER] WARNING: Database connection failed - authentication will not work");
        }
    }
    
    @Override
    public String login(String username, String password) throws RemoteException {
        User user = authService.login(username, password);
        if (user != null) {
            return user.getDisplayName();
        }
        return null;
    }
    
    @Override
    public String register(String username, String password, String displayName, String email) throws RemoteException {
        User user = authService.register(username, password, displayName, email);
        if (user != null) {
            return user.getDisplayName();
        }
        return null;
    }

    @Override
    public synchronized void join(String roomId, long ssrc, String name, int rtpPort, ClientCallback cb) throws RemoteException {
        callbacks.put(ssrc, cb);
        ssrcToRoom.put(ssrc, roomId);

        // Nếu client gửi port 0.0.0.0, có thể là client trên cùng máy server
        // Tạm thời dùng placeholder, server sẽ cập nhật IP thực khi nhận gói RTP đầu tiên trong RtpForwarder
        // Nhưng trong getPeers(), sẽ tự động thay 0.0.0.0 bằng IP LAN của server
        InetSocketAddress placeholder = new InetSocketAddress("0.0.0.0", rtpPort);
        rooms.join(roomId, ssrc, name != null ? name : "User", placeholder);

        // Broadcast peer update via UDP (nhanh hơn RMI callback)
        if (udpControlServer != null) {
            udpControlServer.broadcastPeersUpdate(roomId, rooms.allClients());
        }
        // Vẫn giữ RMI callback làm fallback
        firePeersChanged(roomId);
        ServerDashboard.get().setClients(rooms.allClients());

        System.out.printf("[SERVER] JOIN ssrc=%d name=%s room=%s rtp=%s%n", ssrc, name, roomId, placeholder);
    }

    @Override
    public synchronized void leave(long ssrc) throws RemoteException {
        String room = ssrcToRoom.remove(ssrc);
        callbacks.remove(ssrc);
        rooms.leave(ssrc);
        
        // Remove from UDP control server
        if (udpControlServer != null) {
            udpControlServer.removeClient(ssrc);
        }

        if (room != null) {
            // Broadcast peer update via UDP
            if (udpControlServer != null) {
                udpControlServer.broadcastPeersUpdate(room, rooms.allClients());
            }
            // Vẫn giữ RMI callback làm fallback
            firePeersChanged(room);
        }
        ServerDashboard.get().setClients(rooms.allClients());

        System.out.printf("[SERVER] LEAVE ssrc=%d%n", ssrc);
    }

    @Override
    public synchronized void sendChat(String roomId, long fSsrc, String fromName, String message) throws RemoteException {
        // Nếu có UDP control server, ưu tiên dùng UDP (nhanh hơn)
        // RMI method này chỉ dùng làm fallback nếu client không hỗ trợ UDP
        // Format tên với SSRC để phân biệt: "Name (SSRC)" hoặc "Name #1"
        String displayName = fromName;
        if (fromName != null && !fromName.isEmpty()) {
            long shortId = fSsrc % 1000;
            displayName = fromName + " #" + shortId;
        } else {
            displayName = "User #" + (fSsrc % 1000);
        }
        
        // Fallback: vẫn gửi qua RMI nếu cần
        int sentCount = 0;
        int failedCount = 0;
        for (Map.Entry<Long, ClientCallback> e : callbacks.entrySet()) {
            long targetSsrc = e.getKey();
            if (roomId.equals(ssrcToRoom.get(targetSsrc))) {
                try {
                    e.getValue().chat(roomId, displayName, message, "");
                    sentCount++;
                } catch (Exception ex) {
                    failedCount++;
                }
            }
        }
        if (sentCount > 0 || failedCount > 0) {
            System.out.printf("[SERVER] RMI Chat (fallback): from SSRC=%d, to %d peer(s), failed=%d%n", fSsrc, sentCount, failedCount);
        }
    }

    @Override
    public synchronized void sendPrivateChat(String roomId, long fromSsrc, String fromName, long toSsrc, String message) throws RemoteException {
        // Nếu có UDP control server, ưu tiên dùng UDP (nhanh hơn)
        // RMI method này chỉ dùng làm fallback
        // Fallback: vẫn gửi qua RMI nếu cần
        ClientCallback targetCallback = callbacks.get(toSsrc);
        if (targetCallback != null) {
            String fromRoom = ssrcToRoom.get(fromSsrc);
            String toRoom = ssrcToRoom.get(toSsrc);
            
            if (fromRoom != null && fromRoom.equals(toRoom) && fromRoom.equals(roomId)) {
                try {
                    String displayName = fromName;
                    if (fromName != null && !fromName.isEmpty()) {
                        long shortId = fromSsrc % 1000;
                        displayName = fromName + " #" + shortId;
                    } else {
                        displayName = "User #" + (fromSsrc % 1000);
                    }
                    targetCallback.onPrivateChat(roomId, fromSsrc, displayName, message);
                    System.out.printf("[SERVER] RMI Private Chat (fallback): from SSRC=%d to SSRC=%d%n", fromSsrc, toSsrc);
                } catch (Exception e) {
                    System.err.println("[SERVER] Error sending private chat (fallback): " + e.getMessage());
                }
            }
        }
    }

    @Override
    public synchronized Set<PeerInfo> getPeers(String roomId) {
        return rooms.allClients().stream()
                .filter(ci -> roomId.equals(ci.getRoomId()))
                .map(ci -> {
                    InetSocketAddress ep = ci.getRtpEndpoint();
                    // Nếu endpoint là 0.0.0.0, localhost, hoặc trùng với IP LAN của server
                    // => Client đang chạy trên cùng máy server, thay bằng IP LAN của server
                    if (ep != null) {
                        String epIP = ep.getAddress().getHostAddress();
                        if (ep.getAddress().isAnyLocalAddress() || 
                            ep.getAddress().isLoopbackAddress() ||
                            (serverLANIP != null && epIP.equals(serverLANIP))) {
                            if (serverLANIP != null) {
                                ep = new InetSocketAddress(serverLANIP, ep.getPort());
                            }
                        }
                    }
                    return new PeerInfo(ci.getSsrc(), ci.getRoomId(), ci.getName(), ep);
                })
                .collect(Collectors.toSet());
    }

    private void firePeersChanged(String roomId) {
        Set<PeerInfo> peers = getPeers(roomId);
        int successCount = 0;
        int failedCount = 0;
        System.out.printf("[SERVER] firePeersChanged: room=%s, peers=%d, total callbacks=%d%n",
            roomId, peers.size(), callbacks.size());
        
        for (Map.Entry<Long, ClientCallback> e : callbacks.entrySet()) {
            long ssrc = e.getKey();
            String callbackRoom = ssrcToRoom.get(ssrc);
            if (roomId.equals(callbackRoom)) {
                try {
                    e.getValue().onPeersChanged(roomId, peers);
                    successCount++;
                    System.out.printf("[SERVER] Notified SSRC=%d (room=%s) of peer change%n", ssrc, callbackRoom);
                } catch (Exception ex) {
                    failedCount++;
                    System.err.println("[SERVER] Failed to notify SSRC=" + ssrc + " of peers change: " + ex.getMessage());
                    ex.printStackTrace();
                }
            } else {
                System.out.printf("[SERVER] Skipping SSRC=%d (room=%s, target room=%s)%n", ssrc, callbackRoom, roomId);
            }
        }
        System.out.printf("[SERVER] firePeersChanged complete: room=%s, peers=%d, notified=%d, failed=%d%n",
            roomId, peers.size(), successCount, failedCount);
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
