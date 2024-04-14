import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.io.*;
import java.lang.reflect.Type;
import java.net.InetAddress;
import java.net.Socket;
import java.util.Set;

public class PeerConnection {
    private String ip;
    private Socket connection;
    // reading data from connected peer
    private BufferedReader input;
    // writing data to connected peer
    private BufferedWriter output;
    private PeerConnectionManager connectionManager;
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
            // System.out.println("I am trying to send a message in PeerConnection");
            // System.out.println("Message inside PeerConnection was sent");
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
                        System.out.println("Possible new Ips: " + possibleNewIps);
                        for (String newIp: possibleNewIps) {
                            if (!connectionManager.activePeerConnections.containsKey(newIp) && !newIp.equals(InetAddress.getLocalHost().getHostAddress())) {
                                PeerConnection peerConnection = new PeerConnection(new Socket(newIp, PeerConnectionManager.LISTEN_PORT), connectionManager);
                                connectionManager.addPeerConnection(peerConnection);
                                peerConnection.startReceivingMessages();
                            }
                        }
                    }
                }
            } catch (IOException e) {
                System.out.println("Cannot receive the message from input stream" + e);
            }
        }
    }
}
