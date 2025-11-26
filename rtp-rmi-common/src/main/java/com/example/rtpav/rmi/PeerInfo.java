package com.example.rtpav.rmi;

import java.io.Serializable;
import java.net.InetSocketAddress;

/** Thông tin 1 peer trong phòng. */
public class PeerInfo implements Serializable {
    private final long ssrc;
    private final String roomId;
    private final InetSocketAddress rtpEndpoint; // sẽ cập nhật khi server nhận gói RTP đầu tiên

    public PeerInfo(long ssrc, String roomId, InetSocketAddress rtpEndpoint) {
        this.ssrc = ssrc;
        this.roomId = roomId;
        this.rtpEndpoint = rtpEndpoint;
    }

    public long getSsrc() { return ssrc; }
    public String getRoomId() { return roomId; }
    public InetSocketAddress getRtpEndpoint() { return rtpEndpoint; }
}
