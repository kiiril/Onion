package com.github.kiiril;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import java.net.InetAddress;
import java.net.UnknownHostException;

public class Main {
    static {
        try {
            String ipAddress = InetAddress.getLocalHost().getHostAddress();
            System.setProperty("container.ip", ipAddress);
        } catch (Exception e) {
            System.out.println("Cannot get IP address");
        }
    }
    private static final Logger logger = LogManager.getLogger();

    public static void main(String[] args) throws UnknownHostException {
        logger.info("Application has been started...");
        PeerConnectionManager peerConnectionManager = new PeerConnectionManager();
    }
}
