import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import javax.crypto.SecretKey;
import java.io.*;
import java.lang.reflect.Type;
import java.math.BigInteger;
import java.net.InetAddress;
import java.net.Socket;
import java.util.Set;
import java.util.concurrent.*;

public class PeerConnection {
    private String ip;
    private final Socket connection;
    // reading data from connected peer
    private volatile BufferedReader input;
    // writing data to connected peer
    private BufferedWriter output;
    private final PeerConnectionManager connectionManager;
    private SecretKey symmetricKey;

    private final ExecutorService receiverService = Executors.newFixedThreadPool(5);

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
        System.out.println("Sending message: " + message);
        try {
            output.write(message);
            // needed! https://stackoverflow.com/questions/64249665/socket-is-closed-after-reading-from-its-inputstream\
            output.newLine();
            output.flush();
        } catch (IOException e) {
            System.out.println("Cannot send the message to output stream" + e);
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

    public void establishSharedSecret() {
        System.out.println("Start establishing shared secret with " + getIp());
        // Generate and send DH public key
        String publicKey = DH.getPublicKey().toString();
        String jsonMessage = Util.messageToJson(MessageType.KEY_ESTABLISHMENT, publicKey);
        sendMessage(jsonMessage);
        System.out.println("Sent my public key to " + getIp());

        // Receive and handle peer's DH public key
        String receivedJsonMessage = null; // FIXME this is a bad construction...
        try {
            receivedJsonMessage = input.readLine();
        } catch (IOException e) {
            System.out.println("Cannot receive the message from input stream during key establishment" + e);
        }

        Message receivedMessage = Util.jsonToMessage(receivedJsonMessage);
        System.out.println("Received other public key: " + receivedMessage.getBody());

        if (receivedMessage.getHeader() != MessageType.KEY_ESTABLISHMENT) {
            System.out.println("Unexpected message type during key establishment");
        }

        BigInteger otherPublicKey = new BigInteger(receivedMessage.getBody());
        BigInteger sharedSecret = DH.getSharedSecret(otherPublicKey);
        setSymmetricKey(sharedSecret);

        System.out.println("Symmetric key established with " + getIp());

        startReceivingMessages();
    }


    private class MessageReceiver implements Runnable {
        // TODO identify protocol that the message ends, i.e. with a new line
        @Override
        public void run() {
            System.out.println("Start receiving message in " + Thread.currentThread());
            String str;
            try {
                while ((str = input.readLine()) != null) {
                    System.out.println("Received message: \n" + str);

                    // It is an encrypted message
                    String decryptedText;
                    try {
                        decryptedText = AES.decrypt(str, symmetricKey);
                    } catch (Exception e) {
                        throw new RuntimeException("Decryption problems");
                    }

                    Message receivedMessage = Util.jsonToMessage(decryptedText);
                    MessageType type = receivedMessage.getHeader();
                    System.out.println("Received message: " + type + " " + receivedMessage.getBody());

                    if (type == MessageType.DISCOVERY) {
                        Set<String> possibleNewIps = Util.stringToSet(receivedMessage.getBody());
                        System.out.println("Possible new peers: " + possibleNewIps);
                        for (String newIp : possibleNewIps) {
                            if (!connectionManager.getIps().contains(newIp) && !newIp.equals(InetAddress.getLocalHost().getHostAddress())) {
                                System.out.println("I have a new friend: " + newIp);
                                try {
                                    PeerConnection peerConnection = new PeerConnection(new Socket(newIp, PeerConnectionManager.LISTEN_PORT), connectionManager);
                                    connectionManager.addPeerConnection(peerConnection);
                                    peerConnection.establishSharedSecret(); // FIXME: I think something is broken here
                                } catch (IOException e) {
                                    System.out.println("Cannot create a Socket for a new peer" + e);
                                }
                            }
                        }
                    } else {
                        System.out.println("Regular message: "  + receivedMessage.getBody());
                    }
                }
            } catch (IOException e) {
                System.out.println("Cannot receive the message from input stream" + e);
            }
        }
    }
}
