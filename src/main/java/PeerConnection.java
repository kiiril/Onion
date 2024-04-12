import java.io.*;
import java.net.Socket;

public class PeerConnection {
    private String ip;
    private Socket connection;
    // reading data from connected peer
    private BufferedReader input;
    // writing data to connected peer
    private BufferedWriter output;
    public PeerConnection(Socket connection) {
        this.connection = connection;
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
            // needed! https://stackoverflow.com/questions/64249665/socket-is-closed-after-reading-from-its-inputstream
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
        @Override
        public void run() {
            String str;
            try {
                while ((str = input.readLine()) != null) {
                    System.out.println("Received message: \n" + str);
                }
            } catch (IOException e) {
                System.out.println("Cannot receive the message from input stream" + e);
            }
        }
    }
}
