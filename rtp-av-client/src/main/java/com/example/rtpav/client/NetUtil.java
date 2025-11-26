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
}
