package com.github.kiiril;

import com.github.kiiril.messages.*;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.io.*;
import java.math.BigInteger;
import java.net.Socket;
import java.util.Base64;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.*;

import static com.github.kiiril.PeerConnectionManager.HOST_IP;
import static com.github.kiiril.PeerConnectionManager.LISTEN_PORT;

public class PeerConnection {
    private static final Logger logger = LogManager.getLogger();

    private final String ip;

    private BufferedReader input;
    private BufferedWriter output;

    private final PeerConnectionManager connectionManager;
    private SecretKey symmetricKey;

    private final Map<Integer, SecretKey> sessionKeys = new ConcurrentHashMap<>();
    private final ExecutorService messageListenersHandler = Executors.newCachedThreadPool();
    private final ExecutorService newConnectionsHandler = Executors.newCachedThreadPool();

    public PeerConnection(Socket connection, PeerConnectionManager connectionManager) {
        this.connectionManager = connectionManager;
        this.ip = connection.getInetAddress().getHostAddress();
        try {
            input = new BufferedReader(new InputStreamReader(connection.getInputStream()));
            output = new BufferedWriter(new OutputStreamWriter(connection.getOutputStream()));
        } catch (IOException e) {
            logger.error("Cannot get input or output stream from Socket: {}", e.getMessage());
        }
    }

    public void sendMessage(String message) {
        logger.info("Sending message: {} to {}", message, getIp());
        try {
            output.write(message);
            output.newLine();
            output.flush();
        } catch (IOException e) {
            logger.error("Cannot send the message to output stream: {}", e.getMessage());
        }
    }

    public void sendEncryptedMessage(String message) {
        String encryptedMessage = AES.encrypt(message, symmetricKey);
        sendMessage(encryptedMessage);
    }

    public void startReceivingMessages() {
        // create a new thread for receiving messages from the peer
        logger.info("Starting to receive messages from: {}", getIp());
        messageListenersHandler.submit(new MessageReceiver());
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
            logger.error("Cannot receive the public key from {} during key establishment {}", getIp(), e.getMessage());
            return;
        }

        SymmetricKeyEstablishmentMessage receivedMessage = (SymmetricKeyEstablishmentMessage) Util.jsonToMessage(receivedJsonMessage);

        logger.info("{} public key: {}", getIp(), receivedMessage.getBody());

        if (receivedMessage.getType() != MessageType.SYMMETRIC_KEY_ESTABLISHMENT) {
            logger.warn("Unexpected message type during key establishment");
            return;
        }

        BigInteger otherPublicKey = new BigInteger(receivedMessage.getBody());
        BigInteger sharedSecret = DH.generateSharedSecret(otherPublicKey);
        setSymmetricKey(sharedSecret);

        logger.info("Symmetric key established with {}", getIp());

        startReceivingMessages();
    }

    private void setSymmetricKey(BigInteger sharedSecret) {
        symmetricKey = AES.generateKey(sharedSecret);
    }

    public SecretKey getSymmetricKey() {
        return symmetricKey;
    }

    public void addSessionKey(int sessionId, SecretKey sessionKey) {
        logger.info("(Sender) Save session key for {} with sessionId={} and sessionKey={}", getIp(), sessionId, sessionKey.toString());
        sessionKeys.put(sessionId, sessionKey);
    }

    public SecretKey getSessionKey(int sessionId) {
        logger.info("Get session key for {} with sessionId={} and sessionKey={}", getIp(), sessionId, sessionKeys.get(sessionId).toString());
        return sessionKeys.get(sessionId);
    }

    public String getIp() {
        return ip;
    }

    private class MessageReceiver implements Runnable {
        @Override
        public void run() {
            String receivedJsonMessage;
            try {
                while ((receivedJsonMessage = input.readLine()) != null) {
                    logger.info("Received message: {}", receivedJsonMessage);

                    String decryptedText = AES.decrypt(receivedJsonMessage, symmetricKey);

                    logger.info("Successfully decrypted outer layer");

                    Message receivedMessage = Util.jsonToMessage(decryptedText);
                    MessageType type = receivedMessage.getType();

                    if (type == MessageType.DISCOVERY) {
                        logger.info("Received discovery message");
                        processDiscoveryMessage(receivedMessage);
                    } else if (type == MessageType.FORWARD_MESSAGE) {
                        logger.info("Received forward message");
                        processForwardMessage(receivedMessage);
                    } else if (type == MessageType.BACKWARD_MESSAGE) {
                        logger.info("Received backward message");
                        processBackwardMessage(receivedMessage);
                    } else if (type == MessageType.SESSION_KEY_ESTABLISHMENT) {
                        logger.info("Received session key establishment message");
                        processSessionKeyEstablishmentMessage(receivedMessage);
                    } else {
                        logger.warn("Unexpected message type");
                    }
                }
            } catch (IOException e) {
                logger.error("Cannot receive the message from {}'s input stream: {}", getIp(), e.getMessage());
            }
        }

        private void processDiscoveryMessage(Message receivedMessage) {
            DiscoveryMessage discoveryMessage = (DiscoveryMessage) receivedMessage;
            Set<String> newPossibleIps = Util.stringToSet(discoveryMessage.getBody());
            logger.info("New possible peers: {}", newPossibleIps);

            for (String newIp : newPossibleIps) {
                if (!connectionManager.getActiveIps().contains(newIp) && !newIp.equals(HOST_IP)) {
                    logger.info("New peer found: {}", newIp);
                    try {
                        PeerConnection peerConnection = new PeerConnection(new Socket(newIp, LISTEN_PORT), connectionManager);

                        newConnectionsHandler.submit(() ->{
                            peerConnection.establishSharedSecret();
                            connectionManager.addPeerConnection(peerConnection);
                        }); // we want to receive messages from peer and not be blocked while establishing secret with other peers

                    } catch (IOException e) {
                        logger.error("Cannot create a Socket for a new peer ({})", newIp);
                    }
                }
            }
        }

        private void processForwardMessage(Message receivedMessage) {
            ForwardMessage forwardMessage = (ForwardMessage) receivedMessage;

            Session session = connectionManager.getSession(forwardMessage.getSessionId());
            String decryptedJsonMessage = AES.decrypt(forwardMessage.getBody(), session.getMySessionKey());
            Layer decryptedMessage = (Layer) Util.jsonToMessage(decryptedJsonMessage);

            logger.info("Successfully decrypted layer of encrypted message and get sessionId={}, previousPeer={}, nextPeer={}", forwardMessage.getSessionId(), decryptedMessage.getPreviousPeer(), decryptedMessage.getNextPeer());

            String previousPeerIp = decryptedMessage.getPreviousPeer();
            PeerConnection previousPeerConnection = connectionManager.getActivePeerConnection(previousPeerIp);
            session.setPreviousPeer(previousPeerConnection);

            logger.info("Set previous peer {} for sessionId={}", previousPeerConnection.getIp(), forwardMessage.getSessionId());

            String nextPeerIp = decryptedMessage.getNextPeer();

            if (nextPeerIp == null) {
                logger.info("I am the last peer in the chain with message: {}", decryptedMessage.getBody());

                String response = Util.makeRequest(decryptedMessage.getBody());

                Layer layer = new Layer(response, null, null);
                String jsonMessage = Util.messageToJson(layer);
                String encryptedLayer = AES.encrypt(jsonMessage, session.getMySessionKey());

                logger.info("Encrypted response with the sessionKey={}", session.getMySessionKey());

                BackwardMessage backwardMessage = new BackwardMessage(forwardMessage.getSessionId(), encryptedLayer);
                String jsonBackwardMessage = Util.messageToJson(backwardMessage);
                previousPeerConnection.sendEncryptedMessage(jsonBackwardMessage);

                logger.info("Sent response to the previous peer in the chain");
            } else {
                logger.info("Forward the message to the next peer in the chain: {}", nextPeerIp);

                ForwardMessage message = new ForwardMessage(forwardMessage.getSessionId(), decryptedMessage.getBody());
                String jsonMessage = Util.messageToJson(message);
                PeerConnection nextPeerConnection = connectionManager.getActivePeerConnection(nextPeerIp);

                nextPeerConnection.sendEncryptedMessage(jsonMessage);
            }
        }

        private void processBackwardMessage(Message receivedMessage) {
            BackwardMessage backwardMessage = (BackwardMessage) receivedMessage;

            Session session = connectionManager.getSession(backwardMessage.getSessionId());
            if (session.isSendingPeer()) {
                logger.info("I am the sender and I received the response!");

                String response = session.decryptLayers(backwardMessage.getBody());

                logger.info("Decrypted response: {}", response);

                try (FileWriter fileWriter = new FileWriter(String.format("output/%s-response.html", backwardMessage.getSessionId()), false)) {
                    fileWriter.write(response);
                } catch (IOException e) {
                    System.out.println("Cannot write the response to the file" + e);
                }
            } else {
                logger.info("I am not the sender and I need to forward the message to previous peer");

                Layer layer = new Layer(backwardMessage.getBody(), null, null);
                String jsonMessage = Util.messageToJson(layer);
                String encryptedLayer = AES.encrypt(jsonMessage, session.getMySessionKey());

                logger.info("Encrypted the message with my layer and sessionId={}, sessionKey={}", backwardMessage.getSessionId(), session.getMySessionKey());

                BackwardMessage backwardMessageToSend = new BackwardMessage(backwardMessage.getSessionId(), encryptedLayer);
                String jsonBackwardMessage = Util.messageToJson(backwardMessageToSend);
                PeerConnection previousPeerConnection = session.getPreviousPeer();

                previousPeerConnection.sendEncryptedMessage(jsonBackwardMessage);

                logger.info("Sent the message to the previous peer in the chain: {}", previousPeerConnection.getIp());
            }
        }

        private void processSessionKeyEstablishmentMessage(Message receivedMessage) {
            SessionKeyEstablishmentMessage sessionKeyEstablishmentMessage = (SessionKeyEstablishmentMessage) receivedMessage;

            int sessionId = sessionKeyEstablishmentMessage.getSessionId();
            SecretKey sessionKey = new SecretKeySpec(Base64.getDecoder().decode(sessionKeyEstablishmentMessage.getSessionKey()), "AES");

            connectionManager.createSessionWithKey(sessionId, sessionKey);
        }
    }
}
