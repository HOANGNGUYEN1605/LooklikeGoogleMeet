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

        // Find LAN IP (not virtual network IPs like WSL/Hyper-V)
        String lanIP = findLANIP();
        if (lanIP != null) {
            System.setProperty("java.rmi.server.hostname", lanIP);
            System.out.println("[SERVER] RMI hostname set to LAN IP: " + lanIP);
        } else {
            System.out.println("[SERVER] WARNING: Could not detect LAN IP, RMI may use wrong IP");
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
        
        // UDP Control Server (cho chat và peer updates - giảm lag)
        int udpControlPort = 5005; // Port riêng cho control messages
        UdpControlServer udpControlServer = new UdpControlServer(udpControlPort, rooms);
        Executors.newVirtualThreadPerTaskExecutor().submit(udpControlServer);
        
        // TCP Control Server (cho login, join, leave - đảm bảo và giảm lag)
        int tcpControlPort = 5006; // Port riêng cho TCP control
        TcpControlServer tcpControlServer = new TcpControlServer(tcpControlPort, rooms, udpControlServer);
        Executors.newVirtualThreadPerTaskExecutor().submit(tcpControlServer);
        
        // Pass control server to RMI service để có thể broadcast peer updates (fallback)
        ((RmiConferenceService) svc).setUdpControlServer(udpControlServer);

        // Server UI
        ServerDashboard.launch(udpPort);

        System.out.printf("[SERVER] RMI ready (registry=%d, service=%d), RTP UDP=%d%n", rmiRegistryPort, rmiExportPort, udpPort);
        System.out.println("[SERVER] RMI is listening on all interfaces (0.0.0.0) - accessible from ZeroTier and LAN");
        if (lanIP != null) {
            System.out.println("[SERVER] RMI hostname: " + lanIP + " (clients will connect to this IP)");
        }
        Thread.currentThread().join();
    }
    
    private static String findLANIP() {
        try {
            java.util.Enumeration<java.net.NetworkInterface> interfaces = java.net.NetworkInterface.getNetworkInterfaces();
            while (interfaces.hasMoreElements()) {
                java.net.NetworkInterface ni = interfaces.nextElement();
                // Skip virtual interfaces
                if (ni.isLoopback() || !ni.isUp() || ni.isVirtual()) continue;
                String name = ni.getDisplayName().toLowerCase();
                if (name.contains("virtual") || name.contains("hyper-v") || name.contains("wsl") || 
                    name.contains("zerotier") || name.contains("vmware") || name.contains("virtualbox")) {
                    continue;
                }
                
                java.util.Enumeration<java.net.InetAddress> addresses = ni.getInetAddresses();
                while (addresses.hasMoreElements()) {
                    java.net.InetAddress addr = addresses.nextElement();
                    if (addr instanceof java.net.Inet4Address) {
                        String ip = addr.getHostAddress();
                        // Skip virtual network IPs
                        if (ip.startsWith("192.168.56.") || ip.startsWith("172.24.") || 
                            ip.startsWith("172.30.") || ip.startsWith("169.254.")) {
                            continue;
                        }
                        // Prefer 192.168.x.x or 10.x.x.x (common LAN IPs)
                        if (ip.startsWith("192.168.") || ip.startsWith("10.") || 
                            (ip.startsWith("172.") && !ip.startsWith("172.24.") && !ip.startsWith("172.30."))) {
                            return ip;
                        }
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("[SERVER] Error finding LAN IP: " + e.getMessage());
        }
        return null;
    }
}
