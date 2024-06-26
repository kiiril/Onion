import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.lang.reflect.Type;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.UnknownHostException;
import java.util.*;

public class PeerConnectionManager {
    private final Map<String, PeerConnection> activePeerConnections;
    public static final int LISTEN_PORT = 80;

    public PeerConnectionManager() throws UnknownHostException {
        activePeerConnections = new HashMap<>();
        System.out.println(InetAddress.getLocalHost().getHostAddress());

        try {
            if (!InetAddress.getLocalHost().getHostAddress().equals("172.17.0.2")) {
                System.out.println("I am not a broadcaster.");
                PeerConnection peerConnection = new PeerConnection(new Socket("172.17.0.2", LISTEN_PORT), this);
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
                peerConnection.startReceivingMessages();

                // tell about new one to others
                activePeerConnections.values().parallelStream().forEach(e -> e.sendMessage(convertToJson(MessageType.DISCOVERY, setToString(Collections.singleton(peerConnection.getIp())))));
//                Gson gson = new Gson();
//                Set<String> newIp = Collections.singleton(peerConnection.getIp());
//                String stringSetOfNewIp = gson.toJson(newIp);
//                Message msg = new Message(MessageType.DISCOVERY, stringSetOfNewIp);
//                String jsonMsg = gson.toJson(msg);
//                activePeerConnections.values().parallelStream().forEach(e -> e.sendMessage(jsonMsg));

                // tell new one about others
                peerConnection.sendMessage(convertToJson(MessageType.DISCOVERY, setToString(getIps())));
//                Set<String> ips = getIps();
//                String stringSet = gson.toJson(ips);
//                Message message = new Message(MessageType.DISCOVERY, stringSet);
//                String jsonMessage = gson.toJson(message);
//                peerConnection.sendMessage(jsonMessage);

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

            String encryptedText = AES.encrypt(text, AES.symmetricKey, AES.iv);

            System.out.println("I know these guys: ");
            for (Map.Entry<String, PeerConnection> entry: activePeerConnections.entrySet()) {
                System.out.println(entry.getValue().getIp());
            }
            if (activePeerConnections.isEmpty()) System.out.println("You are alone in the network!");
            else {
                activePeerConnections.values().parallelStream().forEach(e -> e.sendMessage(convertToJson(MessageType.REGULAR, encryptedText)));
            }
            System.out.println("Message was sent.");
        }
    }

    public Set<String> getIps() {
        return activePeerConnections.keySet();
    }

    private String convertToJson(MessageType type, String msg) {
        Gson gson = new Gson();
        Message message = new Message(type, msg);
        return gson.toJson(message);
    }

    private String setToString(Set<String> set) {
        Gson gson = new Gson();
        return gson.toJson(set);
    }
}
