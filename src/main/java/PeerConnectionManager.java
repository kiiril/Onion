import com.google.gson.Gson;

import java.io.IOException;
import java.math.BigInteger;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.UnknownHostException;
import java.util.*;
import java.util.concurrent.CompletableFuture;

public class PeerConnectionManager {
    private final Map<String, PeerConnection> activePeerConnections;
    public static final int LISTEN_PORT = 80;
    public PeerConnectionManager() throws UnknownHostException {
        activePeerConnections = new HashMap<>();
        System.out.println(InetAddress.getLocalHost().getHostAddress());
        DH.generatePublicKey();
        System.out.println("DH public key is generated: " + DH.getPublicKey());

        try {
            if (!InetAddress.getLocalHost().getHostAddress().equals("172.17.0.2")) {
                System.out.println("I am not a broadcaster.");
                PeerConnection peerConnection = new PeerConnection(new Socket("172.17.0.2", LISTEN_PORT), this);
                addPeerConnection(peerConnection);

                peerConnection.establishSharedSecret();
                peerConnection.startReceivingMessages();
            } else {
                System.out.println("I am a broadcaster.");
            }
        } catch (IOException e) {
            System.out.println("Cannot create a Socket for broadcaster" + e);
        }

        new Thread(new Runnable() {
            @Override
            public void run() {
                listenForConnections();
            }
        }).start();

        new Thread(new Runnable() {
            @Override
            public void run() {
                listenForInputFromKeyboard();
            }
        }).start();


    }
    public void addPeerConnection(PeerConnection peerConnection) {
        // logic about id can be changed
        String peerId = peerConnection.getIp();
        activePeerConnections.putIfAbsent(peerId, peerConnection);
    }

    private void removePeerConnection(String peerId) {
        activePeerConnections.remove(peerId);
    }

    public void listenForConnections() {
        try (ServerSocket serverSocket = new ServerSocket(LISTEN_PORT)) {
            while (true) {
                // do not use try-with-resources; socket should not be closed
                Socket requestedConnection = serverSocket.accept();
                PeerConnection peerConnection = new PeerConnection(requestedConnection, this);
                addPeerConnection(peerConnection);
//                peerConnection.startReceivingMessages();

                // Start key establishment in a separate thread
                CompletableFuture<Void> keyEstablishmentFuture = CompletableFuture.runAsync(() -> {
                    try {
                        peerConnection.establishSharedSecret();
                        // After key establishment, start receiving messages
                        peerConnection.startReceivingMessages();
                    } catch (IOException e) {
                        System.out.println("Failed to establish shared secret: " + e);
                    }
                });

                // Block until the key establishment is complete
                keyEstablishmentFuture.join();

                // Notify others about the new peer
                notifyPeersAboutNewPeer(peerConnection);

                // Notify the new peer about existing peers
                notifyNewPeerAboutExistingPeers(peerConnection);
            }

        } catch (IOException e) {
            System.out.println("Cannot create a ServerSocket to listen for connections" + e);
        }
    }

    // Notify all peers about a new peer
    private void notifyPeersAboutNewPeer(PeerConnection newPeerConnection) {
        String jsonMessage = Util.messageToJson(MessageType.DISCOVERY, Util.setToString(Collections.singleton(newPeerConnection.getIp())));
        activePeerConnections.values().parallelStream().forEach(e -> e.sendMessage(AES.encrypt(jsonMessage, e.getSymmetricKey())));
    }

    // Notify a new peer about existing peers
    private void notifyNewPeerAboutExistingPeers(PeerConnection newPeerConnection) {
        String jsonMessage = Util.messageToJson(MessageType.DISCOVERY, Util.setToString(getIps()));
        newPeerConnection.sendMessage(AES.encrypt(jsonMessage, newPeerConnection.getSymmetricKey()));
    }

    public void listenForInputFromKeyboard() {
        System.out.println("You can use a console to send messages to other peers.");
        Scanner scanner = new Scanner(System.in);
        while (true) {
            System.out.println("Please enter message that you want to send: ");
            String text = scanner.nextLine();


            String jsonMessage = Util.messageToJson(MessageType.REGULAR, text);

            System.out.println("I know these guys: ");
            for (Map.Entry<String, PeerConnection> entry: activePeerConnections.entrySet()) {
                System.out.println(entry.getValue().getIp());
            }
            if (activePeerConnections.isEmpty()) System.out.println("You are alone in the network!");
            else {
                activePeerConnections.values().parallelStream().forEach(e -> e.sendMessage(AES.encrypt(jsonMessage, e.getSymmetricKey())));
            }
            System.out.println("Message was sent.");
        }
    }

    public Set<String> getIps() {
        return activePeerConnections.keySet();
    }

    public boolean checkIfKeyEstablished(String ip) {
        return activePeerConnections.get(ip).getSymmetricKey() != null;
    }
}
