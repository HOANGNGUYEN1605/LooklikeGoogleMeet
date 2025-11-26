package com.example.rtpav.server;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.util.Map;

public class ServerDashboard extends JFrame {
    private static ServerDashboard INSTANCE;
    public static ServerDashboard get() {
        if (INSTANCE == null) INSTANCE = new ServerDashboard();
        return INSTANCE;
    }

    private final DefaultTableModel model = new DefaultTableModel(
            new Object[]{"SSRC","Room","RTP Endpoint"}, 0);
    private final JLabel status = new JLabel("Total clients: 0");

    private ServerDashboard() {
        super("RTP AV Server Dashboard");
        var table = new JTable(model);
        setLayout(new BorderLayout());
        add(new JScrollPane(table), BorderLayout.CENTER);
        add(status, BorderLayout.SOUTH);
        setSize(720, 420);
        setLocationRelativeTo(null);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
    }

    public void onUpdate(Map<Long, ClientInfo> snapshot) {
        SwingUtilities.invokeLater(() -> {
            model.setRowCount(0);
            snapshot.values().forEach(ci ->
                    model.addRow(new Object[]{
                            ci.getSsrc(),
                            ci.getRoomId(),
                            (ci.getRtpEndpoint()==null?"?":ci.getRtpEndpoint().toString())
                    })
            );
            status.setText("Total clients: " + snapshot.size());
        });
    }

    public static void launch(int udpPort) {
        SwingUtilities.invokeLater(() -> get().setVisible(true));
    }
}
