package com.github.kiiril;

import com.github.kiiril.messages.DiscoveryMessage;
import com.github.kiiril.messages.ForwardMessage;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import javax.crypto.SecretKey;
import java.io.IOException;
import java.net.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class PeerConnectionManager {
    private static final Logger logger = LogManager.getLogger();

    public static final String HOST_IP;
    static {
        try {
            HOST_IP = InetAddress.getLocalHost().getHostAddress();
        } catch (UnknownHostException e) {
            logger.error("Cannot get host IP address", e);
            throw new RuntimeException("Cannot get host IP address", e);
        }
    }
    private static final int NUM_PEERS_IN_CHAIN = 3;
    private static final String BROADCASTER_IP = "172.17.0.2";
    public static final int LISTEN_PORT = 80;

    private final Map<String, PeerConnection> activePeerConnections = new ConcurrentHashMap<>();
    private final List<Session> sessions = new ArrayList<>();
    private final ExecutorService newConnectionsHandler = Executors.newCachedThreadPool();

    public PeerConnectionManager() {
        logger.info("Host ip address is: {}", HOST_IP);
        System.out.println("Host ip address is: " + HOST_IP);

        DH.generatePublicKey();
        logger.info("DH public key is generated");

        new Thread(this::listenForConnections).start();
        new Thread(this::listenForInputFromKeyboard).start();

        try {
            if (!HOST_IP.equals(BROADCASTER_IP)) {
                logger.info("I am not a broadcaster");
                PeerConnection peerConnection = new PeerConnection(new Socket(BROADCASTER_IP, LISTEN_PORT), this);

                // can be moved to separate thread, but it is not necessary because main have nothing to do
                peerConnection.establishSharedSecret();
                addPeerConnection(peerConnection);
            } else {
                logger.info("I am a broadcaster");
            }
        } catch (IOException e) {
            logger.error("Cannot create a Socket for broadcaster {}", e.getMessage());
        }
    }

    public void addPeerConnection(PeerConnection peerConnection) {
        String peerId = peerConnection.getIp();
        activePeerConnections.putIfAbsent(peerId, peerConnection);
        logger.info("Added active peer connection: {}", peerConnection.getIp());
    }

    private void removePeerConnection(String peerId) {
        activePeerConnections.remove(peerId);
    }

    public void createSessionWithKey(int sessionId, SecretKey sessionKey) {
        logger.info("Creating session with session id={} and session key={}", sessionId, sessionKey.toString());
        Session session = new Session(sessionId);
        session.setMySessionKey(sessionKey);
        sessions.add(session);
    }

    public Session getSession(int sessionId) {
        Session session = sessions.stream().filter(s -> s.getSessionId() == sessionId).findFirst().orElse(null);
        logger.info("Get session with session id={}", sessionId);
        return session;
    }

    public void listenForConnections() {
        logger.info("Starting to listen for connections...");
        try (ServerSocket serverSocket = new ServerSocket(LISTEN_PORT)) {
            while (true) {
                Socket requestedConnection = serverSocket.accept();
                logger.info("Accepted connection from: {}", requestedConnection.getInetAddress().getHostAddress());

                if (activePeerConnections.containsKey(requestedConnection.getInetAddress().getHostAddress())) continue;

                PeerConnection peerConnection = new PeerConnection(requestedConnection, this);

                newConnectionsHandler.submit(() -> {
                    // Establish shared secret with the new peer
                    peerConnection.establishSharedSecret();
                    // Notify others about the new peer
                    notifyPeersAboutNewPeer(peerConnection);
                    // Notify the new peer about existing peers
                    notifyNewPeerAboutExistingPeers(peerConnection);
                    // Add new peer to active connections
                    addPeerConnection(peerConnection);
                });
            }
        } catch (IOException e) {
            System.out.println("Cannot create a ServerSocket to listen for connections" + e);
        }
    }

    // Notify all peers about a new peer
    private void notifyPeersAboutNewPeer(PeerConnection newPeerConnection) {
        logger.info("Notifying other peers about a new peer: {}", newPeerConnection.getIp());
        DiscoveryMessage message = new DiscoveryMessage(Util.setToString(Collections.singleton(newPeerConnection.getIp())));
        String jsonMessage = Util.messageToJson(message);
        activePeerConnections.values().parallelStream().forEach(e -> e.sendEncryptedMessage(jsonMessage));
    }

    // Notify new peer about existing peers
    private void notifyNewPeerAboutExistingPeers(PeerConnection newPeerConnection) {
        logger.info("Notifying new peer about existing peers: {}", getActiveIps());
        DiscoveryMessage message = new DiscoveryMessage(Util.setToString(getActiveIps()));
        String jsonMessage = Util.messageToJson(message);
        newPeerConnection.sendEncryptedMessage(jsonMessage);
    }

    public void listenForInputFromKeyboard() {
        logger.info("Starting to listen for input from keyboard...");

        System.out.println("You can use a console to make an http request to the server");
        Scanner scanner = new Scanner(System.in);
        while (true) {
            System.out.println("Please enter message that you want to send: ");
            String text = scanner.nextLine();

            int sessionId = (int) (Math.random() * Integer.MAX_VALUE); // fixme uniqueness?
            Session session = new Session(sessionId);
            logger.info("Created a session as sender with session id={}", sessionId);
            PeerConnection[] selectedRouters = session.selectRouters(NUM_PEERS_IN_CHAIN, activePeerConnections);
            sessions.add(session);

            logger.info("Selected peers: {}", Arrays.toString(Arrays.stream(selectedRouters).map(PeerConnection::getIp).toArray()));

            String encryptedJson = session.encryptLayers(text);

            logger.info("I know these peers: {}", Arrays.toString(activePeerConnections.values().stream().map(PeerConnection::getIp).toArray()));

            if (activePeerConnections.isEmpty()) logger.info("No active peers to send message to");
            else {
                PeerConnection firstPeer = selectedRouters[0];
                firstPeer.sendEncryptedMessage(Util.messageToJson(new ForwardMessage(sessionId, encryptedJson)));
                logger.info("Sent message to the first peer in the chain: {}", firstPeer.getIp());
            }
        }
    }

    public Set<String> getActiveIps() {
        return activePeerConnections.keySet();
    }

    public PeerConnection getActivePeerConnection(String ip) {
        return activePeerConnections.get(ip);
    }
}
