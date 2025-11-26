package com.example.rtpav.server;

import com.example.rtpav.rmi.PeerInfo;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

public class RoomManager {
    // roomId -> (ssrc -> ClientInfo)
    private final Map<String, Map<Long, ClientInfo>> rooms = new ConcurrentHashMap<>();
    // ssrc -> roomId
    private final Map<Long, String> ssrcToRoom = new ConcurrentHashMap<>();

    public void join(String roomId, ClientInfo ci) {
        rooms.computeIfAbsent(roomId, k -> new ConcurrentHashMap<>())
                .put(ci.getSsrc(), ci);
        ssrcToRoom.put(ci.getSsrc(), roomId);
    }

    public ClientInfo removeBySsrc(long ssrc) {
        String roomId = ssrcToRoom.remove(ssrc);
        if (roomId == null) return null;
        Map<Long, ClientInfo> rm = rooms.get(roomId);
        if (rm == null) return null;
        ClientInfo removed = rm.remove(ssrc);
        if (rm.isEmpty()) rooms.remove(roomId);
        return removed;
    }

    public ClientInfo bySsrc(long ssrc) {
        String room = ssrcToRoom.get(ssrc);
        if (room == null) return null;
        Map<Long, ClientInfo> rm = rooms.get(room);
        return rm != null ? rm.get(ssrc) : null;
    }

    public Set<PeerInfo> getPeers(String roomId) {
        Map<Long, ClientInfo> rm = rooms.get(roomId);
        if (rm == null) return Set.of();
        return rm.values().stream()
                .map(ci -> new PeerInfo(ci.getSsrc(), roomId, ci.getRtpEndpoint()))
                .collect(Collectors.toSet());
    }

    public Collection<ClientInfo> peers(String roomId) {
        Map<Long, ClientInfo> rm = rooms.get(roomId);
        return rm != null ? rm.values() : List.of();
    }

    public Collection<ClientInfo> peersInSameRoomExcept(long senderSsrc) {
        String room = ssrcToRoom.get(senderSsrc);
        if (room == null) return List.of();
        Map<Long, ClientInfo> rm = rooms.get(room);
        if (rm == null) return List.of();
        return rm.values().stream()
                .filter(ci -> ci.getSsrc() != senderSsrc)
                .collect(Collectors.toList());
    }

    /** snapshot cho dashboard */
    public Map<Long, ClientInfo> snapshotAll() {
        Map<Long, ClientInfo> out = new HashMap<>();
        rooms.values().forEach(m -> out.putAll(m));
        return out;
    }
}
