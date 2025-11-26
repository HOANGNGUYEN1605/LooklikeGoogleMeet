package com.example.rtpav.client;

import com.example.rtpav.rmi.ConferenceService;
import com.example.rtpav.rmi.NetUtil;

import javax.swing.*;
import java.net.InetSocketAddress;
import java.rmi.registry.LocateRegistry;

public class ClientMain {
    public static void main(String[] args) throws Exception {
        String serverHost   = "127.0.0.1";
        int    rmiPort      = 1099;
        int    serverRtp    = 5004;
        int    localRtp     = 6000;
        String roomId       = "demo";
        String name         = "Guest";
        long   ssrc         = System.currentTimeMillis();

        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "--server"     -> serverHost = args[++i];
                case "--rmi"        -> rmiPort    = Integer.parseInt(args[++i]);
                case "--server-rtp" -> serverRtp  = Integer.parseInt(args[++i]);
                case "--room"       -> roomId     = args[++i];
                case "--name"       -> name       = args[++i];
                case "--rtp"        -> localRtp   = Integer.parseInt(args[++i]);
            }
        }

        // Kết nối RMI
        var reg = LocateRegistry.getRegistry(serverHost, rmiPort);
        ConferenceService svc = (ConferenceService) reg.lookup("conference");

        // Địa chỉ RTP
        InetSocketAddress myRtpAddr     = new InetSocketAddress(NetUtil.localhost(), localRtp);
        InetSocketAddress serverRtpAddr = new InetSocketAddress(serverHost, serverRtp);

        // ====== BẮT BUỘC: tạo biến final dùng trong lambda để IDE hết cảnh báo ======
        final String fName       = name;
        final String fRoomId     = roomId;
        final long   fSsrc       = ssrc;
        final int    fRmiPort    = rmiPort;
        final String fServerHost = serverHost;
        final InetSocketAddress fMyRtpAddr     = myRtpAddr;
        final InetSocketAddress fServerRtpAddr = serverRtpAddr;
        final ConferenceService fSvc = svc;

        // Tạo UI + đăng ký callback + join
        SwingUtilities.invokeAndWait(() -> {
            try {
                ClientUI ui = new ClientUI(
                        fName, fRoomId, fSvc, fSsrc,
                        fMyRtpAddr, fServerRtpAddr
                );
                ClientCallbackImpl cb = new ClientCallbackImpl(ui);
                fSvc.join(fRoomId, fSsrc, fMyRtpAddr, cb);
                ui.setVisible(true);
                ui.addChat("[SYSTEM]", "RMI connected to " + fServerHost + ":" + fRmiPort);
            } catch (Exception e) {
                e.printStackTrace();
                JOptionPane.showMessageDialog(
                        null,
                        "Cannot start client: " + e,
                        "Error",
                        JOptionPane.ERROR_MESSAGE
                );
            }
        });
    }
}
