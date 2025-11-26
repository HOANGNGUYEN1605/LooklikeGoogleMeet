package com.example.rtpav.server;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.net.InetSocketAddress;
import java.util.List;

/** Dashboard hiển thị danh sách client. */
public class ServerDashboard {
    private static ServerDashboard INSTANCE;

    public static synchronized ServerDashboard get() { return INSTANCE; }
    public static synchronized void launch(int udpPort) {
        if (INSTANCE == null) INSTANCE = new ServerDashboard(udpPort);
    }

    private final JFrame frame;
    private final DefaultTableModel model;
    private final JLabel status;

    private ServerDashboard(int udpPort) {
        model = new DefaultTableModel(new Object[]{"SSRC", "Room", "RTP Endpoint"}, 0) {
            @Override public boolean isCellEditable(int r, int c) { return false; }
        };
        JTable table = new JTable(model);
        table.setFillsViewportHeight(true);

        status = new JLabel("Total clients: 0");
        frame = new JFrame("RTP AV Server Dashboard (UDP " + udpPort + ")");
        frame.setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
        frame.setLayout(new BorderLayout());
        frame.add(new JScrollPane(table), BorderLayout.CENTER);
        frame.add(status, BorderLayout.SOUTH);
        frame.setSize(640, 420);
        frame.setLocationByPlatform(true);
        frame.setVisible(true);
    }

    /** Đổ lại toàn bộ dữ liệu. Gọi từ server mỗi khi có thay đổi. */
    public void setClients(List<ClientInfo> clients) {
        SwingUtilities.invokeLater(() -> {
            model.setRowCount(0);
            for (ClientInfo ci : clients) {
                String ep = "";
                InetSocketAddress e = ci.getRtpEndpoint();
                if (e != null) ep = e.getAddress().getHostAddress() + ":" + e.getPort();
                model.addRow(new Object[]{ ci.getSsrc(), ci.getRoomId(), ep });
            }
            status.setText("Total clients: " + clients.size());
        });
    }
}
