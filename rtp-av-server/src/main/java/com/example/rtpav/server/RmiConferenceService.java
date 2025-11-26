package com.example.rtpav.server;

import com.example.rtpav.rmi.ClientCallback;
import com.example.rtpav.rmi.ConferenceService;
import com.example.rtpav.rmi.PeerInfo;

import java.net.InetSocketAddress;
import java.rmi.RemoteException;
import java.rmi.server.UnicastRemoteObject;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

public class RmiConferenceService extends UnicastRemoteObject implements ConferenceService {

    private final RoomManager rooms;

    // mapping ssrc -> (room, callback)
    private final Map<Long, ClientCallback> callbacks = new ConcurrentHashMap<>();
    private final Map<Long, String>        ssrcToRoom = new ConcurrentHashMap<>();

    public RmiConferenceService(RoomManager rooms, int exportPort) throws RemoteException {
        super(exportPort);
        this.rooms = rooms;
    }

    @Override
    public synchronized void join(String roomId, long ssrc, int rtpPort, ClientCallback cb) throws RemoteException {
        callbacks.put(ssrc, cb);
        ssrcToRoom.put(ssrc, roomId);

        // endpoint tạm (server sẽ cập nhật IP thực khi nhận gói RTP đầu tiên trong RtpForwarder)
        InetSocketAddress placeholder = new InetSocketAddress("0.0.0.0", rtpPort);
        rooms.join(roomId, ssrc, placeholder);

        firePeersChanged(roomId);
        ServerDashboard.get().setClients(rooms.allClients());

        System.out.printf("[SERVER] JOIN ssrc=%d room=%s rtp=%s%n", ssrc, roomId, placeholder);
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
    public synchronized Set<PeerInfo> getPeers(String roomId) {
        return rooms.allClients().stream()
                .filter(ci -> roomId.equals(ci.getRoomId()))
                .map(ci -> new PeerInfo(ci.getSsrc(), ci.getRoomId(), ci.getRtpEndpoint()))
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
