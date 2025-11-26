package com.example.rtpav.server;

import com.example.rtpav.rmi.ClientCallback;
import com.example.rtpav.rmi.ConferenceService;
import com.example.rtpav.rmi.PeerInfo;

import java.net.InetSocketAddress;
import java.rmi.RemoteException;
import java.rmi.server.UnicastRemoteObject;
import java.util.Collection;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class RmiConferenceService extends UnicastRemoteObject implements ConferenceService {

    private final RoomManager rooms;
    private final Map<Long, ClientCallback> callbacks = new ConcurrentHashMap<>();

    public RmiConferenceService(RoomManager rooms, int exportPort) throws RemoteException {
        super(exportPort); // 0 => random port
        this.rooms = rooms;
    }

    @Override
    public synchronized void join(String roomId, long ssrc, InetSocketAddress rtpEndpoint, ClientCallback callback) throws RemoteException {
        rooms.join(roomId, new ClientInfo(roomId, ssrc, rtpEndpoint));
        callbacks.put(ssrc, callback);
        broadcastPeers(roomId);
    }

    @Override
    public synchronized void leave(long ssrc) throws RemoteException {
        ClientInfo removed = rooms.removeBySsrc(ssrc);
        callbacks.remove(ssrc);
        if (removed != null) broadcastPeers(removed.getRoomId());
    }

    @Override
    public synchronized Set<PeerInfo> getPeers(String roomId) throws RemoteException {
        return rooms.getPeers(roomId);
    }

    @Override
    public synchronized void sendChat(String roomId, String fromName, String message) throws RemoteException {
        Collection<ClientInfo> peers = rooms.peers(roomId);
        for (ClientInfo ci : peers) {
            ClientCallback cb = callbacks.get(ci.getSsrc());
            if (cb == null) continue;
            try { cb.onChatMessage(roomId, fromName, message); }
            catch (Exception ignore) {}
        }
    }

    private void broadcastPeers(String roomId) {
        Set<PeerInfo> peers = rooms.getPeers(roomId);
        for (ClientInfo ci : rooms.peers(roomId)) {
            ClientCallback cb = callbacks.get(ci.getSsrc());
            if (cb == null) continue;
            try { cb.onPeersChanged(roomId, peers); } catch (Exception ignore) {}
        }
        ServerDashboard.get().onUpdate(rooms.snapshotAll());
    }
}
