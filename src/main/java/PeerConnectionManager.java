import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.UnknownHostException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class PeerConnectionManager {

    private final Map<String, PeerConnection> activePeerConnections;
    public static final int LISTEN_PORT = 80;
    private static final String BROADCASTER_IP = "172.17.0.2";

    private final ExecutorService connectionHandlingExecutor = Executors.newCachedThreadPool();

    public PeerConnectionManager() throws UnknownHostException {
        activePeerConnections = new ConcurrentHashMap<>();
        System.out.println(InetAddress.getLocalHost().getHostAddress());
        DH.generatePublicKey();
        System.out.println("DH public key is generated");

        // THIS IS THE FIX OF THE DAY....
        new Thread(this::listenForConnections).start();
        new Thread(this::listenForInputFromKeyboard).start();

        try {
            if (!InetAddress.getLocalHost().getHostAddress().equals(BROADCASTER_IP)) {
                System.out.println("I am not a broadcaster.");
                PeerConnection peerConnection = new PeerConnection(new Socket(BROADCASTER_IP, LISTEN_PORT), this);

                // can be moved to separate thread, but it is not necessary
                peerConnection.establishSharedSecret();
                addPeerConnection(peerConnection);
            } else {
                System.out.println("I am a broadcaster.");
            }
        } catch (IOException e) {
            System.out.println("Cannot create a Socket for broadcaster" + e);
        }
    }

    public void addPeerConnection(PeerConnection peerConnection) {
        // logic about id can be changed
        System.out.println("Add peer connection: " + peerConnection.getIp());
        String peerId = peerConnection.getIp();
        activePeerConnections.putIfAbsent(peerId, peerConnection);
        System.out.println("Active peers: " + activePeerConnections);
    }

    private void removePeerConnection(String peerId) {
        activePeerConnections.remove(peerId);
    }

    public void listenForConnections() {
        try (ServerSocket serverSocket = new ServerSocket(LISTEN_PORT)) {
            while (true) {
                // do not use try-with-resources; socket should not be closed
                Socket requestedConnection = serverSocket.accept();
                System.out.println("Accepted connection from: " + requestedConnection.getInetAddress().getHostAddress());

                if (activePeerConnections.containsKey(requestedConnection.getInetAddress().getHostAddress())) continue;

                PeerConnection peerConnection = new PeerConnection(requestedConnection, this);

                connectionHandlingExecutor.submit(() -> {
                    // Establish shared secret with the new peer
                    peerConnection.establishSharedSecret();
                    // Notify others about the new peer
                    notifyPeersAboutNewPeer(peerConnection);
                    // Notify the new peer about existing peers
                    notifyNewPeerAboutExistingPeers(peerConnection);
                    // Add new peer
                    addPeerConnection(peerConnection);
                });
            }

        } catch (IOException e) {
            System.out.println("Cannot create a ServerSocket to listen for connections" + e);
        }
    }

    // Notify all peers about a new peer
    private void notifyPeersAboutNewPeer(PeerConnection newPeerConnection) {
        System.out.println("Notifying peers about a new peer: " + newPeerConnection.getIp());
        String jsonMessage = Util.messageToJson(MessageType.DISCOVERY, Util.setToString(Collections.singleton(newPeerConnection.getIp())));
        activePeerConnections.values().parallelStream().forEach(e -> e.sendMessage(AES.encrypt(jsonMessage, e.getSymmetricKey())));
    }

    // Notify a new peer about existing peers
    private void notifyNewPeerAboutExistingPeers(PeerConnection newPeerConnection) {
        System.out.println("Notifying a new peer about existing peers: " + getIps());
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
