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

    private final Map<String, PeerConnection> activePeerConnections;
    public static final int LISTEN_PORT = 80;
    private static final String BROADCASTER_IP = "172.17.0.2";
    private static final int NUM_PEERS_IN_CHAIN = 2;

    private final List<Session> sessions = new ArrayList<>();

    private final ExecutorService connectionHandlingExecutor = Executors.newCachedThreadPool();

    public PeerConnectionManager() throws UnknownHostException {
        activePeerConnections = new ConcurrentHashMap<>();
        logger.info("My ip address is: {}", InetAddress.getLocalHost().getHostAddress());
        DH.generatePublicKey();
        logger.info("DH public key is generated");

        // THIS IS THE FIX OF THE DAY....
        new Thread(this::listenForConnections).start();
        new Thread(this::listenForInputFromKeyboard).start();

        try {
            if (!InetAddress.getLocalHost().getHostAddress().equals(BROADCASTER_IP)) {
                logger.info("I am not a broadcaster");
                PeerConnection peerConnection = new PeerConnection(new Socket(BROADCASTER_IP, LISTEN_PORT), this);

                // can be moved to separate thread, but it is not necessary
                peerConnection.establishSharedSecret();
                addPeerConnection(peerConnection);
            } else {
                logger.info("I am a broadcaster");
            }
        } catch (IOException e) {
            System.out.println("Cannot create a Socket for broadcaster" + e);
        }
    }

    public void addPeerConnection(PeerConnection peerConnection) {
        // logic about id can be changed
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
                // do not use try-with-resources; socket should not be closed
                Socket requestedConnection = serverSocket.accept();
                logger.info("Accepted connection from: {}", requestedConnection.getInetAddress().getHostAddress());

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
        logger.info("Notifying other peers about a new peer: {}", newPeerConnection.getIp());
        DiscoveryMessage message = new DiscoveryMessage(Util.setToString(Collections.singleton(newPeerConnection.getIp())));
        String jsonMessage = Util.messageToJson(message);
        activePeerConnections.values().parallelStream().forEach(e -> e.sendMessage(AES.encrypt(jsonMessage, e.getSymmetricKey())));
    }

    // Notify new peer about existing peers
    private void notifyNewPeerAboutExistingPeers(PeerConnection newPeerConnection) {
        logger.info("Notifying new peer about existing peers: {}", getIps());
        DiscoveryMessage message = new DiscoveryMessage(Util.setToString(getIps()));
        String jsonMessage = Util.messageToJson(message);
        newPeerConnection.sendMessage(AES.encrypt(jsonMessage, newPeerConnection.getSymmetricKey()));
    }

    public void listenForInputFromKeyboard() {
        logger.info("Starting to listen for input from keyboard...");

        System.out.println("You can use a console to send messages to other peers.");
        Scanner scanner = new Scanner(System.in);
        while (true) {
            System.out.println("Please enter message that you want to send: ");
            String text = scanner.nextLine();

            PeerConnection[] selectedPeers = selectPeers(); // 2 peers
            logger.info("Selected peers: {}", Arrays.toString(Arrays.stream(selectedPeers).map(PeerConnection::getIp).toArray()));

            int sessionId = (int) (Math.random() * Integer.MAX_VALUE); // fixme uniqueness?
            Session session = new Session(sessionId);
            session.setSelectedPeers(selectedPeers);
            sessions.add(session);
            logger.info("Created a session as sender with session id={}", sessionId);

            String encryptedJson = session.encryptWithLayers(text);

            logger.info("I know these peers: {}", Arrays.toString(activePeerConnections.values().stream().map(PeerConnection::getIp).toArray()));

            if (activePeerConnections.isEmpty()) logger.info("No active peers to send message to");
            else {
                PeerConnection firstPeer = selectedPeers[0];
                // No need for additional encryption here, as it's already done in the loop
                firstPeer.sendMessage(AES.encrypt(Util.messageToJson(new ForwardMessage(session.getSessionId(), encryptedJson)), firstPeer.getSymmetricKey()));
                logger.info("Sent message to the first peer in the chain: {}", firstPeer.getIp());
            }
        }
    }

    public Set<String> getIps() {
        return activePeerConnections.keySet();
    }

    public String makeRequest(String stringUrl) {
        logger.info("Making request to: {}", stringUrl);
        try {
            URL url = new URL(stringUrl);
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("GET");

            int responseCode = connection.getResponseCode();
            if (responseCode == HttpURLConnection.HTTP_OK) {
                logger.info("Status code is OK");
                Scanner scanner = new Scanner(connection.getInputStream());
                StringBuilder response = new StringBuilder();
                while (scanner.hasNextLine()) {
                    response.append(scanner.nextLine());
                }
                scanner.close();
                return response.toString();
            } else {
                logger.warn("Cannot get response from URL: {}", stringUrl);
                return "";
            }
        } catch (MalformedURLException e) {
            logger.warn("Cannot create URL from string: {} (error={})", stringUrl, e);
        } catch (IOException e) {
            logger.warn("Cannot open connection to URL: {} (error={})", stringUrl, e);
        }
        return "";
    }

    // TODO forward function

    private PeerConnection[] selectPeers() {
        if (activePeerConnections.size() < NUM_PEERS_IN_CHAIN) {
            logger.info("Not enough peers to select from, returning all peers");
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

    public Map<String, PeerConnection> getActivePeerConnections() {
        return activePeerConnections;
    }
}
