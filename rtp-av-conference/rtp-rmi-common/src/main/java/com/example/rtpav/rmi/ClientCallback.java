package com.example.rtpav.rmi;

import java.rmi.Remote;
import java.rmi.RemoteException;
import java.util.Set;

public interface ClientCallback extends Remote {
    void onPeersChanged(String roomId, Set<PeerInfo> peers) throws RemoteException;
    void onChatMessage(String roomId, String fromName, String message) throws RemoteException;
}
