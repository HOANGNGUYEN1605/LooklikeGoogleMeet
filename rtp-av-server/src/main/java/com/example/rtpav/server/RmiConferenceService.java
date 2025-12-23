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

    // mapping ssrc -> (room, callback)
    private final Map<Long, ClientCallback> callbacks = new ConcurrentHashMap<>();
    private final Map<Long, String>        ssrcToRoom = new ConcurrentHashMap<>();

    public RmiConferenceService(RoomManager rooms, int exportPort) throws RemoteException {
        super(exportPort);
        this.rooms = rooms;
        this.authService = new AuthService();
        
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

        // endpoint tạm (server sẽ cập nhật IP thực khi nhận gói RTP đầu tiên trong RtpForwarder)
        InetSocketAddress placeholder = new InetSocketAddress("0.0.0.0", rtpPort);
        rooms.join(roomId, ssrc, name != null ? name : "User", placeholder);

        firePeersChanged(roomId);
        ServerDashboard.get().setClients(rooms.allClients());

        System.out.printf("[SERVER] JOIN ssrc=%d name=%s room=%s rtp=%s%n", ssrc, name, roomId, placeholder);
    }

    @Override
    public synchronized void leave(long ssrc) throws RemoteException {
        String room = ssrcToRoom.remove(ssrc);
        callbacks.remove(ssrc);
        rooms.leave(ssrc);

        if (room != null) {
            firePeersChanged(room);
        }
        ServerDashboard.get().setClients(rooms.allClients());

        System.out.printf("[SERVER] LEAVE ssrc=%d%n", ssrc);
    }

    @Override
    public synchronized void sendChat(String roomId, long fSsrc, String fromName, String message) throws RemoteException {
        // Format tên với SSRC để phân biệt: "Name (SSRC)" hoặc "Name #1"
        // Lấy số thứ tự dựa trên SSRC (đơn giản hóa: dùng 3 số cuối của SSRC)
        String displayName = fromName;
        if (fromName != null && !fromName.isEmpty()) {
            // Thêm số thứ tự đơn giản (3 số cuối của SSRC) để phân biệt
            long shortId = fSsrc % 1000; // Lấy 3 số cuối
            displayName = fromName + " #" + shortId;
        } else {
            displayName = "User #" + (fSsrc % 1000);
        }
        
        for (Map.Entry<Long, ClientCallback> e : callbacks.entrySet()) {
            long targetSsrc = e.getKey();
            if (roomId.equals(ssrcToRoom.get(targetSsrc))) {
                try {
                    e.getValue().chat(roomId, displayName, message, "");
                } catch (Exception ignored) {}
            }
        }
    }

    @Override
    public synchronized void sendPrivateChat(String roomId, long fromSsrc, String fromName, long toSsrc, String message) throws RemoteException {
        System.out.printf("[SERVER] sendPrivateChat: fromSsrc=%d, toSsrc=%d, roomId=%s, message=%s%n", 
            fromSsrc, toSsrc, roomId, message);
        
        // Chỉ gửi đến người nhận cụ thể
        ClientCallback targetCallback = callbacks.get(toSsrc);
        if (targetCallback == null) {
            System.out.printf("[SERVER] ERROR: Target callback not found for SSRC=%d%n", toSsrc);
            System.out.printf("[SERVER] Available callbacks: %s%n", callbacks.keySet());
            return;
        }
        
        // Kiểm tra xem cả hai đều trong cùng room
        String fromRoom = ssrcToRoom.get(fromSsrc);
        String toRoom = ssrcToRoom.get(toSsrc);
        
        System.out.printf("[SERVER] Room check: fromRoom=%s, toRoom=%s, roomId=%s%n", fromRoom, toRoom, roomId);
        
        if (fromRoom != null && fromRoom.equals(toRoom) && fromRoom.equals(roomId)) {
            try {
                // Format tên người gửi
                String displayName = fromName;
                if (fromName != null && !fromName.isEmpty()) {
                    long shortId = fromSsrc % 1000;
                    displayName = fromName + " #" + shortId;
                } else {
                    displayName = "User #" + (fromSsrc % 1000);
                }
                System.out.printf("[SERVER] Sending private chat to SSRC=%d: from=%s, msg=%s%n", 
                    toSsrc, displayName, message);
                targetCallback.onPrivateChat(roomId, fromSsrc, displayName, message);
                System.out.printf("[SERVER] Private chat sent successfully to SSRC=%d%n", toSsrc);
            } catch (Exception e) {
                System.err.println("[SERVER] Error sending private chat: " + e.getMessage());
                e.printStackTrace();
            }
        } else {
            System.out.printf("[SERVER] ERROR: Room mismatch or null. fromRoom=%s, toRoom=%s, roomId=%s%n", 
                fromRoom, toRoom, roomId);
        }
    }

    @Override
    public synchronized Set<PeerInfo> getPeers(String roomId) {
        return rooms.allClients().stream()
                .filter(ci -> roomId.equals(ci.getRoomId()))
                .map(ci -> new PeerInfo(ci.getSsrc(), ci.getRoomId(), ci.getName(), ci.getRtpEndpoint()))
                .collect(Collectors.toSet());
    }

    private void firePeersChanged(String roomId) {
        Set<PeerInfo> peers = getPeers(roomId);
        for (Map.Entry<Long, ClientCallback> e : callbacks.entrySet()) {
            long ssrc = e.getKey();
            if (roomId.equals(ssrcToRoom.get(ssrc))) {
                try {
                    e.getValue().onPeersChanged(roomId, peers);
                } catch (Exception ignored) {}
            }
        }
    }
}
