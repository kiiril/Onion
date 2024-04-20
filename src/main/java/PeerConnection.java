import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.io.*;
import java.lang.reflect.Type;
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

    private class MessageReceiver implements Runnable {
        // TODO identify protocol that the message ends, i.e. with a new line
        @Override
        public void run() {
            String str;
            try {
                while ((str = input.readLine()) != null) {
                    System.out.println("Received message: \n" + str);

                    Gson gson = new Gson();
                    Message receivedMessage = gson.fromJson(str, Message.class);
                    MessageType type = receivedMessage.getHeader();

                    if (type == MessageType.DISCOVERY) {
                        Type setType = new TypeToken<Set<String>>(){}.getType();
                        Set<String> possibleNewIps = gson.fromJson(receivedMessage.getBody(), setType);
                        for (String newIp: possibleNewIps) {
                            if (!connectionManager.getIps().contains(newIp) && !newIp.equals(InetAddress.getLocalHost().getHostAddress())) {
                                System.out.println("I have a new friend: " + newIp);
                                PeerConnection peerConnection = new PeerConnection(new Socket(newIp, PeerConnectionManager.LISTEN_PORT), connectionManager);
                                connectionManager.addPeerConnection(peerConnection);
                                peerConnection.startReceivingMessages();
                            }
                        }
                    } else {
                        try {
                            String decryptedText = AES.decrypt(receivedMessage.getBody(), AES.symmetricKey, AES.iv);
                            System.out.println("Decrypted message: \n" + decryptedText);
                        } catch (Exception e) {
                            System.out.println("Something wrong with decryption");
                        }
                    }
                }
            } catch (IOException e) {
                System.out.println("Cannot receive the message from input stream" + e);
            }
        }
    }
}
