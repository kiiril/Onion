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
    // maybe can be removed
    private String ip;
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
        System.out.println("Sending message: " + message + "to " + getIp());
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
        System.out.println("Started to receive messages from peer: " + getIp());
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
        System.out.println("Add session key in PeerConnection for session " + sessionId + "with key: " + sessionKey);
        sessionKeys.put(sessionId, sessionKey);
    }

    public SecretKey getSessionKey(int sessionId) {
        System.out.println("Get session key in PeerConnection for session " + sessionId + "with key: " + sessionKeys.get(sessionId));
        return sessionKeys.get(sessionId);
    }

    public void establishSharedSecret() {
        System.out.println("Start establishing shared secret with " + getIp());

        // Send my DH public key
        String publicKey = DH.getPublicKey().toString();
        SymmetricKeyEstablishmentMessage message = new SymmetricKeyEstablishmentMessage(publicKey);
        String jsonMessage = Util.messageToJson(message);

        sendMessage(jsonMessage);
        System.out.println("Sent my public key to " + getIp());

        // Receive and handle peer's DH public key
        String receivedJsonMessage; // FIXME this is a bad construction...
        try {
            receivedJsonMessage = input.readLine();
        } catch (IOException e) {
            System.out.println("Cannot receive the public key from another peer during key establishment" + e);
            return;
        }

        SymmetricKeyEstablishmentMessage receivedMessage = (SymmetricKeyEstablishmentMessage) Util.jsonToMessage(receivedJsonMessage);

        System.out.println("Received another peer's public key: " + receivedMessage.getBody());

        if (receivedMessage.getType() != MessageType.SYMMETRIC_KEY_ESTABLISHMENT) {
            System.out.println("Unexpected message type during key establishment");
            return;
        }

        BigInteger otherPublicKey = new BigInteger(receivedMessage.getBody());
        BigInteger sharedSecret = DH.getSharedSecret(otherPublicKey);
        setSymmetricKey(sharedSecret);

        System.out.println("Symmetric key established with " + getIp());

        startReceivingMessages();
    }

    // TODO: when you get the regular message then you need to save where it came from and wait until backpropagation
    private class MessageReceiver implements Runnable {
        // TODO identify protocol that the message ends, i.e. with a new line
        @Override
        public void run() {
            System.out.println("Start receiving message in " + Thread.currentThread());
            String str;
            try {
                while ((str = input.readLine()) != null) {
                    System.out.println("Received message: \n" + str);

                    // Message is encrypted
                    String decryptedText;
                    try {
                        decryptedText = AES.decrypt(str, symmetricKey);
                    } catch (Exception e) { // fixme catch exceptions inside the decrypt method
                        throw new RuntimeException("Decryption problems");
                    }
                    Message receivedMessage = Util.jsonToMessage(decryptedText);
                    MessageType type = receivedMessage.getType();

                    if (type == MessageType.DISCOVERY) {
                        DiscoveryMessage discoveryMessage = (DiscoveryMessage) receivedMessage;
                        Set<String> possibleNewIps = Util.stringToSet(discoveryMessage.getBody());
                        System.out.println("New possible peers: " + possibleNewIps);

                        for (String newIp : possibleNewIps) {
                            if (!connectionManager.getIps().contains(newIp) && !newIp.equals(InetAddress.getLocalHost().getHostAddress())) {
                                System.out.println("I have a new peer: " + newIp);
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
                    } else if (type == MessageType.REGULAR) {
                        System.out.println("Received regular message");
                        RegularMessage regularMessage = (RegularMessage) receivedMessage;

                        String decryptedJsonMessage = AES.decrypt(regularMessage.getBody(), connectionManager.getSessionKey(regularMessage.getSessionId()));
                        RegularMessage decryptedMessage = (RegularMessage) Util.jsonToMessage(decryptedJsonMessage);
                        // get the message id and use session key for decryption
                        System.out.println("Regular message: " + decryptedMessage.getBody() + " with " + decryptedMessage.getNextPeer() + " and " + decryptedMessage.getPreviousPeer());

                        String nextPeer = decryptedMessage.getNextPeer();
                        if (nextPeer == null) {
                            System.out.println("This is the last peer in the chain with message: " + decryptedMessage.getBody());
                        } else {
                            // forward the message to the next peer
                            RegularMessage message = new RegularMessage(decryptedMessage.getSessionId(), decryptedMessage.getBody(), null, null);
                            String jsonMessage = Util.messageToJson(message);
                            PeerConnection nextPeerConnection = connectionManager.getActivePeerConnections().get(nextPeer);
                            nextPeerConnection.sendMessage(AES.encrypt(jsonMessage, nextPeerConnection.getSymmetricKey()));
                        }
                    } else if (type == MessageType.SESSION_KEY_ESTABLISHMENT) {
                        SessionKeyEstablishmentMessage sessionKeyEstablishmentMessage = (SessionKeyEstablishmentMessage) receivedMessage;

                        int sessionId = sessionKeyEstablishmentMessage.getSessionId();
                        SecretKey sessionKey = new SecretKeySpec(Base64.getDecoder().decode(sessionKeyEstablishmentMessage.getSessionKey()), "AES");
                        // save the session key in some storage
                        connectionManager.addSessionKey(sessionId, sessionKey);
                    }else {
                        System.out.println("Unexpected message type");
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
