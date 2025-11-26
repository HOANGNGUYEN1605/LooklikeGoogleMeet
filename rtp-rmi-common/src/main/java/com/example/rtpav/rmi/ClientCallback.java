package com.example.rtpav.rmi;

import java.rmi.Remote;
import java.rmi.RemoteException;
import java.util.Set;

/**
 * Callback mà server gọi ngược về client.
 * (Đồng bộ với bản 1.0.1: chat có 4 tham số String)
 */
public interface ClientCallback extends Remote {

    /** Server thông báo danh sách peers thay đổi. */
    void onPeersChanged(String roomId, Set<PeerInfo> peers) throws RemoteException;

    /**
     * Nhận 1 tin nhắn chat.
     * Tham số thứ 4 'reserved' để tương thích ngược (không cần dùng).
     */
    void chat(String roomId, String fromName, String message, String reserved) throws RemoteException;
}
