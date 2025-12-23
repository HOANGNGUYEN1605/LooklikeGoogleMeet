package com.example.rtpav.server;

import java.net.InetSocketAddress;

public class ClientInfo {
    private final long ssrc;
    private final String roomId;
    private final String name; // Tên người dùng
    private volatile InetSocketAddress rtpEndpoint; // có thể cập nhật khi server nhận được RTP

    public ClientInfo(String roomId, long ssrc, String name, InetSocketAddress ep) {
        this.roomId = roomId;
        this.ssrc = ssrc;
        this.name = name;
        this.rtpEndpoint = ep;
    }

    public long getSsrc() { return ssrc; }
    public String getRoomId() { return roomId; }
    public String getName() { return name; }
    public InetSocketAddress getRtpEndpoint() { return rtpEndpoint; }
    public void setRtpEndpoint(InetSocketAddress ep) { this.rtpEndpoint = ep; }

    @Override public String toString() {
        return "ClientInfo{room=" + roomId + ", ssrc=" + ssrc + ", ep=" + rtpEndpoint + "}";
    }
}
