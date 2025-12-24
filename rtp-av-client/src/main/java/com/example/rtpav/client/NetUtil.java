package com.example.rtpav.client;

import java.net.InetAddress;
import java.net.InetSocketAddress;

public final class NetUtil {
    private NetUtil() {}

    public static InetAddress localhost() {
        try { return InetAddress.getLocalHost(); }
        catch (Exception e) { throw new RuntimeException(e); }
    }

    public static InetSocketAddress endpoint(String host, int port) {
        try { return new InetSocketAddress(InetAddress.getByName(host), port); }
        catch (Exception e) { throw new RuntimeException(e); }
    }
    
    /**
     * Tìm IP LAN thực của máy (bỏ qua virtual networks như WSL, Hyper-V, VirtualBox)
     */
    public static String findLANIP() {
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
            System.err.println("[CLIENT] Error finding LAN IP: " + e.getMessage());
        }
        // Fallback: dùng localhost
        try {
            return InetAddress.getLocalHost().getHostAddress();
        } catch (Exception e) {
            return "127.0.0.1";
        }
    }
}
