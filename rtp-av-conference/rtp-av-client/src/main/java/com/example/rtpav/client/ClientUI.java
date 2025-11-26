package com.example.rtpav.client;

import com.example.rtpav.client.media.AudioIO;
import com.example.rtpav.client.media.VideoCapture;
import com.example.rtpav.client.ui.VideoRenderer;
import com.example.rtpav.rmi.ConferenceService;
import com.example.rtpav.rmi.PeerInfo;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.rmi.RemoteException;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;

public class ClientUI extends JFrame {

    // UI
    private final VideoRenderer selfView = new VideoRenderer();
    private final JPanel peersPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
    private final JTextArea chatArea = new JTextArea(8, 50);
    private final JTextField chatInput = new JTextField();
    private final JButton btnSend = new JButton("Send");
    private final JButton btnCam = new JButton("Camera ON");
    private final JButton btnMic = new JButton("Mic ON");

    // network & state
    private final ConferenceService svc;
    private final String roomId;
    private final String name;
    private final long ssrc;
    private final InetSocketAddress myRtp;
    private final InetSocketAddress serverRtp;

    private DatagramSocket udp;
    private volatile boolean camOn = false;
    private volatile boolean micOn = false;
    private VideoCapture cam;
    private AudioIO audio;

    private Image avatar;

    // map peerSSRC -> VideoRenderer
    private final Map<Long, VideoRenderer> peerViews = new ConcurrentHashMap<>();

    public ClientUI(String name,
                    String roomId,
                    ConferenceService svc,
                    long ssrc,
                    InetSocketAddress myRtp,
                    InetSocketAddress serverRtp) throws Exception {
        super("Client - " + name + "@" + roomId);
        this.name = name;
        this.roomId = roomId;
        this.svc = svc;
        this.ssrc = ssrc;
        this.myRtp = myRtp;
        this.serverRtp = serverRtp;

        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setSize(1024, 720);
        setLocationRelativeTo(null);

        chatArea.setEditable(false);
        var chatBox = new JPanel(new BorderLayout());
        chatBox.add(new JScrollPane(chatArea), BorderLayout.CENTER);
        var chatSend = new JPanel(new BorderLayout());
        chatSend.add(chatInput, BorderLayout.CENTER);
        chatSend.add(btnSend, BorderLayout.EAST);
        chatBox.add(chatSend, BorderLayout.SOUTH);

        var top = new JPanel(new FlowLayout(FlowLayout.LEFT));
        top.add(btnCam);
        top.add(btnMic);

        var left = new JPanel(new BorderLayout());
        left.add(new JLabel("Self view"), BorderLayout.NORTH);
        left.add(selfView, BorderLayout.CENTER);

        var right = new JPanel(new BorderLayout());
        right.add(new JLabel("Peers"), BorderLayout.NORTH);
        var scrollPeers = new JScrollPane(peersPanel);
        right.add(scrollPeers, BorderLayout.CENTER);

        var center = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, left, right);
        center.setResizeWeight(0.4);

        setLayout(new BorderLayout());
        add(top, BorderLayout.NORTH);
        add(center, BorderLayout.CENTER);
        add(chatBox, BorderLayout.SOUTH);

        // load avatar
        var url = ClientUI.class.getResource("/avatar_default.png");
        if (url != null) {
            avatar = new ImageIcon(url).getImage();
        } else {
            // fallback avatar
            avatar = new BufferedImage(320, 180, BufferedImage.TYPE_INT_RGB);
            Graphics g = avatar.getGraphics();
            g.setColor(Color.LIGHT_GRAY); g.fillRect(0,0,320,180);
            g.setColor(Color.BLACK); g.drawString("Avatar", 140, 90);
            g.dispose();
        }
        selfView.showAvatar(avatar);

        // wire actions
        btnCam.addActionListener(this::toggleCam);
        btnMic.addActionListener(this::toggleMic);
        btnSend.addActionListener(e -> sendChat());
        chatInput.addActionListener(e -> sendChat());

        // UDP
        udp = new DatagramSocket(myRtp.getPort());
        udp.setSendBufferSize(1<<20);
        udp.setReceiveBufferSize(1<<20);

        // Start receiver
        Executors.newVirtualThreadPerTaskExecutor().submit(this::udpReceiverLoop);

        addChat("[SYSTEM]", "Joined room " + roomId + ", UDP bind=" + myRtp);
    }

    public void updatePeers(Set<PeerInfo> peers) {
        // build UI boxes for peers (not including self)
        peersPanel.removeAll();
        for (PeerInfo p : peers) {
            if (p.getSsrc() == ssrc) continue;
            VideoRenderer vr = peerViews.computeIfAbsent(p.getSsrc(), k -> new VideoRenderer());
            peersPanel.add(wrapWithTitle(vr, "Peer " + p.getSsrc()));
        }
        peersPanel.revalidate();
        peersPanel.repaint();
        addChat("[SYSTEM]","Peers = " + peers.size());
    }

    public void addChat(String from, String msg) {
        chatArea.append(from + ": " + msg + "\n");
    }

    private JPanel wrapWithTitle(JComponent c, String title) {
        JPanel box = new JPanel(new BorderLayout());
        box.add(new JLabel(title), BorderLayout.NORTH);
        box.add(c, BorderLayout.CENTER);
        return box;
    }

    private void sendChat() {
        String text = chatInput.getText().trim();
        if (text.isEmpty()) return;
        chatInput.setText("");
        try {
            svc.sendChat(roomId, name, text);
        } catch (RemoteException e) {
            addChat("[ERROR]", e.toString());
        }
    }

    private void toggleCam(ActionEvent e) {
        camOn = !camOn;
        btnCam.setText(camOn ? "Camera OFF" : "Camera ON");
        if (camOn) {
            if (cam == null) cam = new VideoCapture(640, 360);
            // start sender loop if not running
            Executors.newVirtualThreadPerTaskExecutor().submit(this::videoSendLoop);
        } else {
            if (cam != null) { try { cam.close(); } catch (Exception ignore) {} cam = null; }
            selfView.showAvatar(avatar);
        }
    }

    private void toggleMic(ActionEvent e) {
        micOn = !micOn;
        btnMic.setText(micOn ? "Mic OFF" : "Mic ON");
        if (micOn) {
            try {
                if (audio == null) { audio = new AudioIO(); audio.open(); audio.start(); }
                Executors.newVirtualThreadPerTaskExecutor().submit(this::audioSendLoop);
            } catch (Exception ex) {
                addChat("[ERROR]", "Audio init: " + ex);
                micOn = false;
                btnMic.setText("Mic ON");
            }
        } else {
            // keep speaker running for remote peers; just stop sending mic
        }
    }

    private void videoSendLoop() {
        byte[] hdr = new byte[9];
        ByteBuffer bb = ByteBuffer.wrap(hdr);
        while (camOn) {
            try {
                byte[] jpeg = (cam == null) ? null : cam.captureJpeg();
                if (jpeg != null) {
                    bb.clear();
                    bb.putLong(ssrc);
                    bb.put((byte)0); // mediaType=0 video
                    byte[] pkt = new byte[9 + jpeg.length];
                    System.arraycopy(hdr, 0, pkt, 0, 9);
                    System.arraycopy(jpeg, 0, pkt, 9, jpeg.length);
                    udp.send(new DatagramPacket(pkt, pkt.length, serverRtp));
                    selfView.updateFrame(jpeg); // local preview
                } else {
                    selfView.showAvatar(avatar);
                    Thread.sleep(120);
                }
            } catch (Exception ignore) {}
        }
    }

    private void audioSendLoop() {
        byte[] hdr = new byte[9];
        ByteBuffer bb = ByteBuffer.wrap(hdr);
        byte[] buf = new byte[320]; // 10ms @ 16kHz mono 16-bit -> 160 samples -> 320 bytes
        while (micOn) {
            try {
                int n = (audio == null) ? -1 : audio.readMic(buf);
                if (n > 0) {
                    bb.clear(); bb.putLong(ssrc); bb.put((byte)1); // mediaType=1 audio
                    byte[] pkt = new byte[9 + n];
                    System.arraycopy(hdr, 0, pkt, 0, 9);
                    System.arraycopy(buf, 0, pkt, 9, n);
                    udp.send(new DatagramPacket(pkt, pkt.length, serverRtp));
                } else {
                    Thread.sleep(10);
                }
            } catch (Exception ignore) {}
        }
    }

    private void udpReceiverLoop() {
        byte[] buf = new byte[64*1024];
        DatagramPacket pkt = new DatagramPacket(buf, buf.length);
        while (true) {
            try {
                udp.receive(pkt);
                if (pkt.getLength() < 9) continue;
                ByteBuffer bb = ByteBuffer.wrap(pkt.getData(), 0, pkt.getLength());
                long peerSsrc = bb.getLong();
                byte type = bb.get();

                int payloadLen = pkt.getLength() - 9;
                byte[] payload = new byte[payloadLen];
                System.arraycopy(pkt.getData(), 9, payload, 0, payloadLen);

                if (type == 0) { // video
                    VideoRenderer vr = peerViews.computeIfAbsent(peerSsrc, k -> {
                        VideoRenderer v = new VideoRenderer();
                        peersPanel.add(wrapWithTitle(v, "Peer " + peerSsrc));
                        peersPanel.revalidate();
                        peersPanel.repaint();
                        return v;
                    });
                    vr.updateFrame(payload);
                } else if (type == 1) { // audio
                    if (audio == null) { audio = new AudioIO(); try { audio.open(); audio.start(); } catch (Exception ignore) {} }
                    if (audio != null) audio.play(payload, payload.length);
                }
            } catch (IOException ignore) {}
        }
    }
}
