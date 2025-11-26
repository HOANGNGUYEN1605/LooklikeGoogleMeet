package com.example.rtpav.server;

import java.net.InetSocketAddress;

public class ClientInfo {
    private final long ssrc;
    private final String roomId;
    private volatile InetSocketAddress rtpEndpoint; // có thể cập nhật khi server nhận được RTP

    public ClientInfo(String roomId, long ssrc, InetSocketAddress ep) {
        this.roomId = roomId;
        this.ssrc = ssrc;
        this.rtpEndpoint = ep;
    }

    public long getSsrc() { return ssrc; }
    public String getRoomId() { return roomId; }
    public InetSocketAddress getRtpEndpoint() { return rtpEndpoint; }
    public void setRtpEndpoint(InetSocketAddress ep) { this.rtpEndpoint = ep; }

    @Override public String toString() {
        return "ClientInfo{room=" + roomId + ", ssrc=" + ssrc + ", ep=" + rtpEndpoint + "}";
    }
}
