import javax.crypto.SecretKey;
import javax.net.ssl.HttpsURLConnection;
import java.io.IOException;
import java.math.BigInteger;
import java.net.*;
import java.security.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class PeerConnectionManager {

    private final Map<String, PeerConnection> activePeerConnections;
    public static final int LISTEN_PORT = 80;
    private static final String BROADCASTER_IP = "172.17.0.2";
    private static final int NUM_PEERS_IN_CHAIN = 2;

    private final Map<Integer, SecretKey> mySessionKeys = new ConcurrentHashMap<>();

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

    public void addSessionKey(int sessionId, SecretKey sessionKey) {
        System.out.println("Add session key in PeerConnectionManager for session " + sessionId + "with key: " + sessionKey);
        mySessionKeys.put(sessionId, sessionKey);
    }

    public SecretKey getSessionKey(int sessionId) {
        System.out.println("Get session key in PeerConnectionManager for session " + sessionId + "with key: " + mySessionKeys.get(sessionId));
        return mySessionKeys.get(sessionId);
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
        DiscoveryMessage message = new DiscoveryMessage(Util.setToString(Collections.singleton(newPeerConnection.getIp())));
        String jsonMessage = Util.messageToJson(message);
        activePeerConnections.values().parallelStream().forEach(e -> e.sendMessage(AES.encrypt(jsonMessage, e.getSymmetricKey())));
    }

    // Notify a new peer about existing peers
    private void notifyNewPeerAboutExistingPeers(PeerConnection newPeerConnection) {
        System.out.println("Notifying a new peer about existing peers: " + getIps());
        DiscoveryMessage message = new DiscoveryMessage(Util.setToString(getIps()));
        String jsonMessage = Util.messageToJson(message);
        newPeerConnection.sendMessage(AES.encrypt(jsonMessage, newPeerConnection.getSymmetricKey()));
    }

    public void listenForInputFromKeyboard() {
        System.out.println("You can use a console to send messages to other peers.");
        Scanner scanner = new Scanner(System.in);
        while (true) {
            System.out.println("Please enter message that you want to send: ");
            String text = scanner.nextLine();

            PeerConnection[] selectedPeers = selectPeers(); // 2 peers
            System.out.println("Selected peers: " + Arrays.toString(selectedPeers));
            int sessionId = (int) (Math.random() * Integer.MAX_VALUE); // fixme uniqueness?

            String encryptedJson = encryptWithLayers(text, sessionId, selectedPeers);

            System.out.println("I know these guys: ");
            for (Map.Entry<String, PeerConnection> entry: activePeerConnections.entrySet()) {
                System.out.println(entry.getValue().getIp());
            }

            if (activePeerConnections.isEmpty()) System.out.println("You are alone in the network!");
            else {
                PeerConnection firstPeer = selectedPeers[0];
                // No need for additional encryption here, as it's already done in the loop
                firstPeer.sendMessage(AES.encrypt(Util.messageToJson(new RegularMessage(sessionId, encryptedJson, null, null)), firstPeer.getSymmetricKey()));
                System.out.println("Message was sent.");
            }
        }
    }

    public Set<String> getIps() {
        return activePeerConnections.keySet();
    }

    private String makeRequest(String stringUrl) {
        try {
            URL url = new URL(stringUrl);
            HttpURLConnection connection = (HttpsURLConnection) url.openConnection();
            connection.setRequestMethod("GET");

            int responseCode = connection.getResponseCode();
            if (responseCode == HttpURLConnection.HTTP_OK) {
                Scanner scanner = new Scanner(connection.getInputStream());
                StringBuilder response = new StringBuilder();
                while (scanner.hasNextLine()) {
                    response.append(scanner.nextLine());
                }
                scanner.close();
                return response.toString();
            } else {
                System.out.println("Cannot get response from URL: " + stringUrl);
                return "";
            }
        } catch (MalformedURLException e) {
            System.out.println("Cannot create URL from string: " + stringUrl + e);
        } catch (IOException e) {
            System.out.println("Cannot open connection to URL: " + stringUrl + e);
        }
        return "";
    }

    // TODO forward function

    private PeerConnection[] selectPeers() {
        if (activePeerConnections.size() < NUM_PEERS_IN_CHAIN) {
            return activePeerConnections.values().toArray(new PeerConnection[0]); // return all peers if not enough
        }
        PeerConnection[] selectedPeers = new PeerConnection[NUM_PEERS_IN_CHAIN];
        List<String> allPeers = new ArrayList<>(activePeerConnections.keySet());
        Collections.shuffle(allPeers);
        for (int i = 0; i < NUM_PEERS_IN_CHAIN; i++) {
            selectedPeers[i] = activePeerConnections.get(allPeers.get(i));
        }
        return selectedPeers;
    }

    private String encryptWithLayers(String text, int sessionId, PeerConnection[] selectedPeers) {
        establishSessionKeys(sessionId , selectedPeers);

        String currentPayload = text;
        System.out.println("Original message: " + currentPayload);
        // Perform multilayer encryption
        for (int i = selectedPeers.length - 1; i >= 0; i--) {
            PeerConnection peerConnection = selectedPeers[i];

            // Prepare routing information
            String nextPeer = (i < selectedPeers.length - 1) ? selectedPeers[i + 1].getIp() : null;
            String previousPeer = (i > 0) ? selectedPeers[i - 1].getIp() : null; // change null to your IP

            // Create a new message with the current payload and routing info
            RegularMessage layerMessage = new RegularMessage(sessionId, currentPayload, nextPeer, previousPeer);
            System.out.println("Layer message: " + layerMessage.getBody() + " with " + layerMessage.getNextPeer() + " and " + layerMessage.getPreviousPeer());

            // Convert the entire layer message to JSON and encrypt it
            String jsonLayerMessage = Util.messageToJson(layerMessage);

            System.out.println("Encrypt message with " + peerConnection.getSessionKey(sessionId));
            currentPayload = AES.encrypt(jsonLayerMessage, peerConnection.getSessionKey(sessionId));
        }

        return currentPayload;
    }

    // fixme can be parallelized
    private void establishSessionKeys(int sessionId, PeerConnection[] selectedPeers) {
        for (PeerConnection selectedPeer : selectedPeers) {
            BigInteger randomSecret = new BigInteger(2048, new SecureRandom());
            SecretKey sessionKey = AES.generateKey(randomSecret);

            selectedPeer.addSessionKey(sessionId, sessionKey);

            // Send the session key to all peers in the chain
            SessionKeyEstablishmentMessage message = new SessionKeyEstablishmentMessage(sessionId, Base64.getEncoder().encodeToString(sessionKey.getEncoded()));
            String jsonMessage = Util.messageToJson(message);
            selectedPeer.sendMessage(AES.encrypt(jsonMessage, selectedPeer.getSymmetricKey()));
        }
    }

    public Map<String, PeerConnection> getActivePeerConnections() {
        return activePeerConnections;
    }
}
