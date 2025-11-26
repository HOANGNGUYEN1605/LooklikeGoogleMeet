package com.example.rtpav.server;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;

/**
 * Forwarder UDP đơn giản:
 * Packet format:
 *  [0..7]   : SSRC (long, big-endian)
 *  [8]      : mediaType (0=video,1=audio)
 *  [9..end] : payload (JPEG cho video, PCM 16k mono 16-bit cho audio)
 */
public class RtpForwarder implements Runnable {
    private static final Logger log = LoggerFactory.getLogger(RtpForwarder.class);

    private final int udpPort;
    private final RoomManager rooms;

    public RtpForwarder(int udpPort, RoomManager rooms) {
        this.udpPort = udpPort;
        this.rooms = rooms;
    }

    @Override
    public void run() {
        try (DatagramSocket sock = new DatagramSocket(udpPort)) {
            sock.setReceiveBufferSize(1<<20);
            sock.setSendBufferSize(1<<20);
            byte[] buf = new byte[64*1024];
            var pkt = new DatagramPacket(buf, buf.length);
            log.info("RTP forwarder listening UDP {}", udpPort);

            while (true) {
                sock.receive(pkt);
                if (pkt.getLength() < 9) continue;

                ByteBuffer bb = ByteBuffer.wrap(pkt.getData(), 0, pkt.getLength());
                long ssrc = bb.getLong();
                byte mediaType = bb.get();

                ClientInfo sender = rooms.bySsrc(ssrc);
                if (sender == null) continue;

                sender.setRtpEndpoint(new InetSocketAddress(pkt.getAddress(), pkt.getPort()));

                // forward to peers in same room
                for (ClientInfo peer : rooms.peersInSameRoomExcept(ssrc)) {
                    var dst = peer.getRtpEndpoint();
                    if (dst == null) continue;
                    var out = new DatagramPacket(pkt.getData(), pkt.getLength(), dst.getAddress(), dst.getPort());
                    sock.send(out);
                }
            }
        } catch (Exception e) {
            log.error("RTP forwarder error", e);
        }
    }
}
