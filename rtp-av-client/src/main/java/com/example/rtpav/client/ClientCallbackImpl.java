package com.example.rtpav.client;

import com.example.rtpav.rmi.ClientCallback;
import com.example.rtpav.rmi.PeerInfo;

import java.rmi.RemoteException;
import java.rmi.server.UnicastRemoteObject;
import java.util.Set;

/** Cài đặt callback cho client; đồng bộ với interface (chat có 4 String). */
public class ClientCallbackImpl extends UnicastRemoteObject implements ClientCallback {

    private final ClientUI ui;

    public ClientCallbackImpl(ClientUI ui) throws RemoteException {
        super(0); // export ngẫu nhiên
        this.ui = ui;
    }

    @Override
    public void onPeersChanged(String roomId, Set<PeerInfo> peers) throws RemoteException {
        ui.setPeers(peers);
        ui.addChat("[SYSTEM]", "Peers = " + peers.size());
    }

    @Override
    public void chat(String roomId, String fromName, String message, String reserved) throws RemoteException {
        ui.addChat(fromName, message);
    }

    @Override
    public void onPrivateChat(String roomId, long fromSsrc, String fromName, String message) throws RemoteException {
        System.out.printf("[CLIENT] Received private chat: fromSsrc=%d, fromName=%s, msg=%s%n", 
            fromSsrc, fromName, message);
        ui.addPrivateChat(fromSsrc, fromName, message);
    }
}
