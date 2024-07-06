import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import javax.crypto.SecretKey;
import java.io.*;
import java.lang.reflect.Type;
import java.math.BigInteger;
import java.net.InetAddress;
import java.net.Socket;
import java.util.Set;

public class PeerConnection {
    private String ip;
    private final Socket connection;
    // reading data from connected peer
    private BufferedReader input;
    // writing data to connected peer
    private BufferedWriter output;
    private final PeerConnectionManager connectionManager;
    private SecretKey symmetricKey;
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
        System.out.println("Started to receive messages from another peer...");
        new Thread(new MessageReceiver()).start();
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

    public void establishSharedSecret() throws IOException {
        // Generate and send DH public key
        String publicKey = DH.getPublicKey().toString();
        String jsonMessage = Util.messageToJson(MessageType.KEY_ESTABLISHMENT, publicKey);
        sendMessage(jsonMessage);

        // Receive and handle peer's DH public key
        String receivedJsonMessage = input.readLine();
        Message receivedMessage = Util.jsonToMessage(receivedJsonMessage);
        BigInteger otherPublicKey = new BigInteger(receivedMessage.getBody());
        BigInteger sharedSecret = DH.getSharedSecret(otherPublicKey);
        setSymmetricKey(sharedSecret);

        System.out.println("Symmetric key established with " + getIp());
    }


    private class MessageReceiver implements Runnable {
        // TODO identify protocol that the message ends, i.e. with a new line
        @Override
        public void run() {
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

                    if (type == MessageType.DISCOVERY) {
                        Set<String> possibleNewIps = Util.stringToSet(receivedMessage.getBody());
                        for (String newIp : possibleNewIps) {
                            if (!connectionManager.getIps().contains(newIp) && !newIp.equals(InetAddress.getLocalHost().getHostAddress())) {
                                System.out.println("I have a new friend: " + newIp);
                                PeerConnection peerConnection = new PeerConnection(new Socket(newIp, PeerConnectionManager.LISTEN_PORT), connectionManager);
                                connectionManager.addPeerConnection(peerConnection);
                                peerConnection.startReceivingMessages();
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
