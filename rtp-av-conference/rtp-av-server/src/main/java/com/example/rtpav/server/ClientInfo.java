package com.example.rtpav.server;

import java.net.InetSocketAddress;

public class ClientInfo {
    private final String roomId;
    private final long ssrc;
    private volatile InetSocketAddress rtpEndpoint;

    public ClientInfo(String roomId, long ssrc, InetSocketAddress rtpEndpoint) {
        this.roomId = roomId;
        this.ssrc = ssrc;
        this.rtpEndpoint = rtpEndpoint;
    }

    public String getRoomId() { return roomId; }
    public long getSsrc() { return ssrc; }

    public synchronized InetSocketAddress getRtpEndpoint() { return rtpEndpoint; }
    public synchronized void setRtpEndpoint(InetSocketAddress ep) { this.rtpEndpoint = ep; }
}
