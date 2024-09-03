import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.io.*;
import java.math.BigInteger;
import java.net.InetAddress;
import java.net.Socket;
import java.util.Base64;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.*;

public class PeerConnection {
    private static final Logger logger = LogManager.getLogger();

    // maybe can be removed
    private final String ip;
    private final Socket connection;

    // reading data from connected peer
    private BufferedReader input;
    // writing data to connected peer
    private BufferedWriter output;
    private final PeerConnectionManager connectionManager;
    private SecretKey symmetricKey;

    private final Map<Integer, SecretKey> sessionKeys = new ConcurrentHashMap<>();

    // FIXME technically if send messages at the same time from more than 5 peers it will not receive all of them (for fixed thread pool)
    private final ExecutorService receiverService = Executors.newCachedThreadPool();
    private final ExecutorService connectionHandlingExecutor = Executors.newCachedThreadPool();

    public PeerConnection(Socket connection, PeerConnectionManager connectionManager) {
        this.connection = connection;
        this.connectionManager = connectionManager;
        this.ip = connection.getInetAddress().getHostAddress();
        try {
            input = new BufferedReader(new InputStreamReader(connection.getInputStream()));
            output = new BufferedWriter(new OutputStreamWriter(connection.getOutputStream()));
        } catch (IOException e) {
            System.out.println("Cannot get input or output stream from Socket" + e);
        }
    }

    public void sendMessage(String message) {
        logger.info("Sending message: {} to {}", message, getIp());
        try {
            output.write(message);
            // needed! https://stackoverflow.com/questions/64249665/socket-is-closed-after-reading-from-its-inputstream\
            output.newLine();
            output.flush();
        } catch (IOException e) {
            System.out.println("Cannot send the message to output stream" + e);
            // TODO retry to send the message
        }
    }

    public void startReceivingMessages() {
        // create a new thread for receiving messages from the PeerConnection
        logger.info("Starting to receive messages from peer: {}", getIp());
        receiverService.submit(new MessageReceiver());
    }

    public String getIp() {
        return ip;
    }

    public void setSymmetricKey(BigInteger sharedSecret) {
        symmetricKey = AES.generateKey(sharedSecret);
    }

    public SecretKey getSymmetricKey() {
        return symmetricKey;
    }

    public void addSessionKey(int sessionId, SecretKey sessionKey) {
        logger.info("As sender save session key for {} with sessionId={} and sessionKey={}", getIp(), sessionId, sessionKey.toString());
        sessionKeys.put(sessionId, sessionKey);
    }

    public SecretKey getSessionKey(int sessionId) {
        logger.info("Get session key for {} with sessionId={} and sessionKey={}", getIp(), sessionId, sessionKeys.get(sessionId).toString());
        return sessionKeys.get(sessionId);
    }

    public void establishSharedSecret() {
        logger.info("Starting to establish shared secret with {}", getIp());

        // Send my DH public key
        String publicKey = DH.getPublicKey().toString();
        SymmetricKeyEstablishmentMessage message = new SymmetricKeyEstablishmentMessage(publicKey);
        String jsonMessage = Util.messageToJson(message);

        sendMessage(jsonMessage);
        logger.info("Sent my public key to {}", getIp());

        // Receive and handle peer's DH public key
        String receivedJsonMessage;
        try {
            receivedJsonMessage = input.readLine();
            logger.info("Received public key from {}", getIp());
        } catch (IOException e) {
            System.out.println("Cannot receive the public key from another peer during key establishment" + e);
            return;
        }

        SymmetricKeyEstablishmentMessage receivedMessage = (SymmetricKeyEstablishmentMessage) Util.jsonToMessage(receivedJsonMessage);

        logger.info("{} public key: {}", getIp(), receivedMessage.getBody());

        if (receivedMessage.getType() != MessageType.SYMMETRIC_KEY_ESTABLISHMENT) {
            logger.warn("Unexpected message type during key establishment");
            return;
        }

        BigInteger otherPublicKey = new BigInteger(receivedMessage.getBody());
        BigInteger sharedSecret = DH.getSharedSecret(otherPublicKey);
        setSymmetricKey(sharedSecret);

        logger.info("Symmetric key established with {}", getIp());

        startReceivingMessages();
    }

    private class MessageReceiver implements Runnable {
        // TODO identify protocol that the message ends, i.e. with a new line
        @Override
        public void run() {
            String str;
            try {
                while ((str = input.readLine()) != null) {
                    logger.info("Received message: {}", str);

                    // Message is encrypted
                    String decryptedText;
                    decryptedText = AES.decrypt(str, symmetricKey);

                    logger.info("Successfully decrypted outer layer");

                    Message receivedMessage = Util.jsonToMessage(decryptedText);
                    MessageType type = receivedMessage.getType();

                    if (type == MessageType.DISCOVERY) {
                        logger.info("Received discovery message");
                        DiscoveryMessage discoveryMessage = (DiscoveryMessage) receivedMessage;
                        Set<String> possibleNewIps = Util.stringToSet(discoveryMessage.getBody());
                        logger.info("New possible peers: {}", possibleNewIps);

                        for (String newIp : possibleNewIps) {
                            if (!connectionManager.getIps().contains(newIp) && !newIp.equals(InetAddress.getLocalHost().getHostAddress())) {
                                logger.info("New peer found: {}", newIp);
                                try {
                                    PeerConnection peerConnection = new PeerConnection(new Socket(newIp, PeerConnectionManager.LISTEN_PORT), connectionManager);

                                    connectionHandlingExecutor.submit(() ->{
                                            peerConnection.establishSharedSecret();
                                            connectionManager.addPeerConnection(peerConnection); // mb fine, mb no, I do not know :)
                                    }); // we want to receive messages from peer and not be blocked while establishing secret with other peers

                                } catch (IOException e) {
                                    System.out.println("Cannot create a Socket for a new peer" + e);
                                }
                            }
                        }
                    } else if (type == MessageType.FORWARD_MESSAGE) {
                        logger.info("Received forward message");

                        ForwardMessage forwardMessage = (ForwardMessage) receivedMessage;

                        Session session = connectionManager.getSession(forwardMessage.getSessionId());
                        String decryptedJsonMessage = AES.decrypt(forwardMessage.getBody(), session.getMySessionKey());
                        Layer decryptedMessage = (Layer) Util.jsonToMessage(decryptedJsonMessage);

                        logger.info("Successfully decrypted layer of encrypted message and get sessionId={}, previousPeer={}, nextPeer={}", forwardMessage.getSessionId(), decryptedMessage.getPreviousPeer(), decryptedMessage.getNextPeer());

                        String previousPeer = decryptedMessage.getPreviousPeer();
                        PeerConnection previousPeerConnection = connectionManager.getActivePeerConnections().get(previousPeer);
                        session.setPreviousPeer(previousPeerConnection);
                        logger.info("Set previous peer {} for sessionId={}", previousPeerConnection.getIp(), forwardMessage.getSessionId());

                        String nextPeer = decryptedMessage.getNextPeer();

                        if (nextPeer == null) {
                            logger.info("I am the last peer in the chain with message: {}", decryptedMessage.getBody());
                            String response = connectionManager.makeRequest(decryptedMessage.getBody());

                            Layer layer = new Layer(response, null, null);
                            String jsonMessage = Util.messageToJson(layer);
                            String encryptedLayer = AES.encrypt(jsonMessage, session.getMySessionKey());
                            logger.info("Encrypted response with the sessionKey={}", session.getMySessionKey());

                            BackwardMessage backwardMessage = new BackwardMessage(forwardMessage.getSessionId(), encryptedLayer);
                            String jsonBackwardMessage = Util.messageToJson(backwardMessage);
                            previousPeerConnection.sendMessage(AES.encrypt(jsonBackwardMessage, previousPeerConnection.getSymmetricKey()));

                            logger.info("Sent response to the previous peer in the chain");
                        } else {
                            logger.info("Forward the message to the next peer in the chain");

                            ForwardMessage message = new ForwardMessage(forwardMessage.getSessionId(), decryptedMessage.getBody());
                            String jsonMessage = Util.messageToJson(message);
                            PeerConnection nextPeerConnection = connectionManager.getActivePeerConnections().get(nextPeer);

                            nextPeerConnection.sendMessage(AES.encrypt(jsonMessage, nextPeerConnection.getSymmetricKey()));
                        }
                    } else if (type == MessageType.BACKWARD_MESSAGE) {
                        logger.info("Received backward message");
                        BackwardMessage backwardMessage = (BackwardMessage) receivedMessage;

                        Session session = connectionManager.getSession(backwardMessage.getSessionId());
                        if (session.isSendingPeer()) {
                            logger.info("I am the sender and I received the response");
                            String response = session.decryptWithLayers(backwardMessage.getBody());

                            logger.info("Decrypted response: {}", response);

                            try (FileWriter fileWriter = new FileWriter(String.format("output/%s-response.html", backwardMessage.getSessionId()), false)) {
                                fileWriter.write(response);
                            } catch (IOException e) {
                                System.out.println("Cannot write the response to the file" + e);
                            }
                        } else {
                            logger.info("I am not the sender and I need to forward the message");
                            Layer layer = new Layer(backwardMessage.getBody(), null, null);
                            String jsonMessage = Util.messageToJson(layer);
                            String encryptedLayer = AES.encrypt(jsonMessage, session.getMySessionKey());
                            logger.info("Encrypted the message with my layer and sessionId={}, sessionKey={}", backwardMessage.getSessionId(), session.getMySessionKey());

                            BackwardMessage backwardMessageToSend = new BackwardMessage(backwardMessage.getSessionId(), encryptedLayer);
                            String jsonBackwardMessage = Util.messageToJson(backwardMessageToSend);

                            session.getPreviousPeer().sendMessage(AES.encrypt(jsonBackwardMessage, session.getPreviousPeer().getSymmetricKey()));
                            logger.info("Sent the message to the previous peer in the chain");
                        }

                    } else if (type == MessageType.SESSION_KEY_ESTABLISHMENT) {
                        logger.info("Received session key establishment message");
                        SessionKeyEstablishmentMessage sessionKeyEstablishmentMessage = (SessionKeyEstablishmentMessage) receivedMessage;

                        int sessionId = sessionKeyEstablishmentMessage.getSessionId();
                        SecretKey sessionKey = new SecretKeySpec(Base64.getDecoder().decode(sessionKeyEstablishmentMessage.getSessionKey()), "AES");
                        // save the session key in some storage
                        connectionManager.createSessionWithKey(sessionId, sessionKey);
                    } else {
                        logger.warn("Unexpected message type");
                    }
                }
            } catch (IOException e) {
                System.out.println("Cannot receive the message from input stream" + e);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
    }
}
