package com.example.rtpav.server;

import com.example.rtpav.rmi.ConferenceService;

import java.net.InetAddress;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.RMISocketFactory;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.Executors;

public class ServerMain {
    public static void main(String[] args) throws Exception {
        int udpPort = 5004;
        int rmiRegistryPort = 1099;
        int rmiExportPort  = 2099;

        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "--udp"     -> udpPort = Integer.parseInt(args[++i]);
                case "--rmi-reg" -> rmiRegistryPort = Integer.parseInt(args[++i]);
                case "--rmi-svc" -> rmiExportPort  = Integer.parseInt(args[++i]);
            }
        }

        // Create custom RMISocketFactory to bind RMI to all interfaces (0.0.0.0)
        // This allows remote clients (including ZeroTier) to connect
        RMISocketFactory.setSocketFactory(new RMISocketFactory() {
            @Override
            public ServerSocket createServerSocket(int port) throws java.io.IOException {
                ServerSocket socket = new ServerSocket(port, 0, InetAddress.getByName("0.0.0.0"));
                System.out.println("[SERVER] RMI binding to 0.0.0.0:" + port + " (all interfaces)");
                return socket;
            }

            @Override
            public Socket createSocket(String host, int port) throws java.io.IOException {
                return new Socket(host, port);
            }
        });

        RoomManager rooms = new RoomManager();

        // RMI
        Registry reg = LocateRegistry.createRegistry(rmiRegistryPort);
        ConferenceService svc = new RmiConferenceService(rooms, rmiExportPort);
        reg.rebind("conference", svc);

        // RTP forwarder
        Executors.newVirtualThreadPerTaskExecutor().submit(new RtpForwarder(udpPort, rooms));

        // Server UI
        ServerDashboard.launch(udpPort);

        System.out.printf("[SERVER] RMI ready (registry=%d, service=%d), RTP UDP=%d%n", rmiRegistryPort, rmiExportPort, udpPort);
        System.out.println("[SERVER] RMI is listening on all interfaces (0.0.0.0) - accessible from ZeroTier and LAN");
        Thread.currentThread().join();
    }
}
