package com.example.rtpav.rmi;

import java.io.Serializable;
import java.net.InetSocketAddress;

public class PeerInfo implements Serializable {
    private static final long serialVersionUID = 1L;

    private final long ssrc;
    private final String roomId;
    private final InetSocketAddress rtpEndpoint;

    public PeerInfo(long ssrc, String roomId, InetSocketAddress rtpEndpoint) {
        this.ssrc = ssrc;
        this.roomId = roomId;
        this.rtpEndpoint = rtpEndpoint;
    }

    public long getSsrc() { return ssrc; }
    public String getRoomId() { return roomId; }
    public InetSocketAddress getRtpEndpoint() { return rtpEndpoint; }

    @Override
    public String toString() {
        return "PeerInfo{ssrc=" + ssrc + ", room='" + roomId + "', rtp=" + rtpEndpoint + "}";
    }
}
