package com.example.rtpav.rmi;

import java.net.InetSocketAddress;
import java.rmi.Remote;
import java.rmi.RemoteException;
import java.util.Set;

public interface ConferenceService extends Remote {
    void join(String roomId, long ssrc, InetSocketAddress rtpEndpoint, ClientCallback callback) throws RemoteException;
    void leave(long ssrc) throws RemoteException;

    Set<PeerInfo> getPeers(String roomId) throws RemoteException;

    // Chat text (broadcast trong room)
    void sendChat(String roomId, String fromName, String message) throws RemoteException;
}
