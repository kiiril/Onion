import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.UnknownHostException;
import java.util.HashMap;
import java.util.Map;
import java.util.Scanner;

public class PeerConnectionManager {
    private final Map<String, PeerConnection> activePeerConnections;
    public static final int LISTEN_PORT = 80;

    public PeerConnectionManager() throws UnknownHostException {
        activePeerConnections = new HashMap<>();
        System.out.println(InetAddress.getLocalHost().getHostAddress());

        try {
            if (!InetAddress.getLocalHost().getHostAddress().equals("172.17.0.2")) {
                System.out.println("I am not a broadcaster.");
                PeerConnection peerConnection = new PeerConnection(new Socket("172.17.0.2", LISTEN_PORT));
                // TODO send hello message to broadcaster and get the list of other peers
                addPeerConnection(peerConnection);
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
    private void addPeerConnection(PeerConnection peerConnection) {
        String peerId = peerConnection.getIp();
        activePeerConnections.put(peerId, peerConnection);
    }

    private void removePeerConnection(String peerId) {
        activePeerConnections.remove(peerId);
    }

    public void listenForConnections() {
        try (ServerSocket serverSocket = new ServerSocket(LISTEN_PORT)) {
            while (true) {
                // do not use try-with-resources; socket should not be closed
                Socket requestedConnection = serverSocket.accept();
                PeerConnection peerConnection = new PeerConnection(requestedConnection);
                addPeerConnection(peerConnection);
                peerConnection.startReceivingMessages();
            }

        } catch (IOException e) {
            System.out.println("Cannot create a ServerSocket to listen for connections" + e);
        }
    }

    public void listenForInputFromKeyboard() {
        System.out.println("You can use a console to send messages to other peers.");
        Scanner scanner = new Scanner(System.in);
        while (true) {
            System.out.println("Please enter message that you want to send: ");
            String text = scanner.nextLine();
            System.out.println("I know these guys: ");
            for (Map.Entry<String, PeerConnection> entry: activePeerConnections.entrySet()) {
                System.out.println(entry.getValue().getIp());
            }
            activePeerConnections.values().parallelStream().forEach(e -> e.sendMessage(text));
            System.out.println("Message was sent.");
        }
    }
}
