package com.example.rtpav.rmi;

import java.rmi.Remote;
import java.rmi.RemoteException;
import java.util.Set;

/**
 * Dịch vụ RMI cho hội thảo:
 * - đăng nhập/đăng ký
 * - join/leave phòng
 * - gửi chat text
 * - lấy danh sách peers trong phòng
 *
 * LƯU Ý: phiên bản này nhận RTP port (int) khi join.
 */
public interface ConferenceService extends Remote {
    /**
     * Đăng nhập - trả về display name nếu thành công, null nếu thất bại
     */
    String login(String username, String password) throws RemoteException;
    
    /**
     * Đăng ký user mới - trả về display name nếu thành công, null nếu thất bại
     */
    String register(String username, String password, String displayName, String email) throws RemoteException;
    
    /** Client join vào room. rtpPort là UDP port mà client bind. */
    void join(String roomId, long ssrc, String name, int rtpPort, ClientCallback callback) throws RemoteException;

    /** Client rời phòng. */
    void leave(long ssrc) throws RemoteException;

    /** Gửi chat text trong phòng. */
    void sendChat(String roomId, long fSsrc, String fromName, String message) throws RemoteException;

    /** Gửi chat riêng đến một peer cụ thể. */
    void sendPrivateChat(String roomId, long fromSsrc, String fromName, long toSsrc, String message) throws RemoteException;

    /** Lấy danh sách peers trong phòng. */
    Set<PeerInfo> getPeers(String roomId) throws RemoteException;
}
