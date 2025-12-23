package com.example.rtpav.server;

import java.net.InetSocketAddress;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/** Quản lý phòng & client (thread-safe). */
public class RoomManager {
    // roomId -> (ssrc -> ClientInfo)
    private final Map<String, Map<Long, ClientInfo>> rooms = new ConcurrentHashMap<>();
    // ssrc -> roomId
    private final Map<Long, String> ssrcToRoom = new ConcurrentHashMap<>();

    public void join(String roomId, long ssrc, String name, InetSocketAddress ep) {
        rooms.computeIfAbsent(roomId, k -> new ConcurrentHashMap<>())
                .put(ssrc, new ClientInfo(roomId, ssrc, name, ep));
        ssrcToRoom.put(ssrc, roomId);
    }

    public ClientInfo leave(long ssrc) {
        String room = ssrcToRoom.remove(ssrc);
        if (room == null) return null;
        Map<Long, ClientInfo> rm = rooms.get(room);
        if (rm == null) return null;
        ClientInfo removed = rm.remove(ssrc);
        if (rm.isEmpty()) rooms.remove(room);
        return removed;
    }

    public ClientInfo bySsrc(long ssrc) {
        String room = ssrcToRoom.get(ssrc);
        if (room == null) return null;
        Map<Long, ClientInfo> rm = rooms.get(room);
        if (rm == null) return null;
        return rm.get(ssrc);
    }

    /** Cập nhật endpoint khi forwarder nhận được gói RTP. */
    public void updateEndpoint(long ssrc, InetSocketAddress ep) {
        ClientInfo ci = bySsrc(ssrc);
        if (ci != null) ci.setRtpEndpoint(ep);
    }

    /** Lấy toàn bộ clients (để đổ ra dashboard). */
    public List<ClientInfo> allClients() {
        return rooms.values().stream()
                .flatMap(m -> m.values().stream())
                .collect(Collectors.toList());
    }
}
