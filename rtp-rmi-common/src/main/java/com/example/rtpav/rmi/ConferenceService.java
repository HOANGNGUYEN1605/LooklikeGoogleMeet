package com.example.rtpav.rmi;

import java.rmi.Remote;
import java.rmi.RemoteException;
import java.util.Set;

/**
 * Dịch vụ RMI cho hội thảo:
 * - join/leave phòng
 * - gửi chat text
 * - lấy danh sách peers trong phòng
 *
 * LƯU Ý: phiên bản này nhận RTP port (int) khi join.
 */
public interface ConferenceService extends Remote {
    /** Client join vào room. rtpPort là UDP port mà client bind. */
    void join(String roomId, long ssrc, int rtpPort, ClientCallback callback) throws RemoteException;

    /** Client rời phòng. */
    void leave(long ssrc) throws RemoteException;

    /** Gửi chat text trong phòng. */
    void sendChat(String roomId, long fSsrc, String fromName, String message) throws RemoteException;

    /** Lấy danh sách peers trong phòng. */
    Set<PeerInfo> getPeers(String roomId) throws RemoteException;
}
