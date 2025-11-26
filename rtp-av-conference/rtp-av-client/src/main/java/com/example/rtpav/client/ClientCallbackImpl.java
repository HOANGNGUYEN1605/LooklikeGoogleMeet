package com.example.rtpav.client;

import com.example.rtpav.rmi.ClientCallback;
import com.example.rtpav.rmi.PeerInfo;

import javax.swing.*;
import java.rmi.RemoteException;
import java.rmi.server.UnicastRemoteObject;
import java.util.Set;

public class ClientCallbackImpl extends UnicastRemoteObject implements ClientCallback {
    private final ClientUI ui;

    public ClientCallbackImpl(ClientUI ui) throws RemoteException {
        super(0);
        this.ui = ui;
    }

    @Override
    public void onPeersChanged(String roomId, Set<PeerInfo> peers) throws RemoteException {
        SwingUtilities.invokeLater(() -> ui.updatePeers(peers));
    }

    @Override
    public void onChatMessage(String roomId, String fromName, String message) throws RemoteException {
        SwingUtilities.invokeLater(() -> ui.addChat(fromName, message));
    }
}
