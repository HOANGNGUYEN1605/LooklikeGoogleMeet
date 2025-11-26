package com.example.rtpav.rmi;

import java.net.InetAddress;
import java.net.InetSocketAddress;

public final class NetUtil {
    private NetUtil(){}

    public static InetSocketAddress sock(String host, int port) {
        return new InetSocketAddress(host, port);
    }

    public static String localhost() {
        try { return InetAddress.getLocalHost().getHostAddress(); }
        catch (Exception e) { return "127.0.0.1"; }
    }
}
