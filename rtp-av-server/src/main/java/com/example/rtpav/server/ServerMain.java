package com.example.rtpav.server;

import com.example.rtpav.rmi.ConferenceService;

import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
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

        RoomManager rooms = new RoomManager();

        // RMI
        Registry reg = LocateRegistry.createRegistry(rmiRegistryPort);
        ConferenceService svc = new RmiConferenceService(rooms, rmiExportPort);
        reg.rebind("conference", svc);

        // RTP forwarder
        Executors.newVirtualThreadPerTaskExecutor().submit(new RtpForwarder(udpPort, rooms));

        // Server UI
        ServerDashboard.launch(udpPort);

        System.out.printf("RMI ready (registry=%d, service=%d), RTP UDP=%d%n", rmiRegistryPort, rmiExportPort, udpPort);
        Thread.currentThread().join();
    }
}
