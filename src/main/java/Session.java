import javax.crypto.SecretKey;
import java.math.BigInteger;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.security.SecureRandom;
import java.util.Base64;

public class Session {
    private final int sessionId;
    private PeerConnection[] selectedPeers;
    private SecretKey mySessionKey;
    private PeerConnection previousPeer;

    public Session(int sessionId) {
        this.sessionId = sessionId;
    }

    public int getSessionId() {
        return sessionId;
    }

    public void setSelectedPeers(PeerConnection[] selectedPeers) {
        this.selectedPeers = selectedPeers;
    }

    public SecretKey getMySessionKey() {
        return mySessionKey;
    }

    public void setMySessionKey(SecretKey mySessionKey) {
        this.mySessionKey = mySessionKey;
    }

    public PeerConnection getPreviousPeer() {
        return previousPeer;
    }

    public void setPreviousPeer(PeerConnection previousPeer) {
        this.previousPeer = previousPeer;
    }

    public PeerConnection[] getSelectedPeers() {
        return selectedPeers;
    }

    public boolean isSendingPeer() {
        return previousPeer == null;
    }

    public String encryptWithLayers(String text) {
        establishSessionKeys();

        String currentPayload = text;
        System.out.println("Original message: " + currentPayload);
        // Perform multilayer encryption
        for (int i = selectedPeers.length - 1; i >= 0; i--) {
            PeerConnection peerConnection = selectedPeers[i];

            try {
                // Prepare routing information
                String nextPeer = (i < selectedPeers.length - 1) ? selectedPeers[i + 1].getIp() : null;
                String previousPeer = (i > 0) ? selectedPeers[i - 1].getIp() : InetAddress.getLocalHost().getHostAddress(); // change null to your IP

                // Create a new message with the current payload and routing info
                Layer layerMessage = new Layer(currentPayload, nextPeer, previousPeer);
                System.out.println("Layer message: " + layerMessage.getBody() + " with " + layerMessage.getNextPeer() + " and " + layerMessage.getPreviousPeer());

                // Convert the entire layer message to JSON and encrypt it
                String jsonLayerMessage = Util.messageToJson(layerMessage);

                System.out.println("Encrypt message with " + peerConnection.getSessionKey(sessionId));
                currentPayload = AES.encrypt(jsonLayerMessage, peerConnection.getSessionKey(sessionId));
            } catch (UnknownHostException e) {
                System.out.println("Cannot get local host address: " + e);
            }
        }
        return currentPayload;
    }

    public String decryptWithLayers(String encrypted) {
        String currentPayload = encrypted;
        // Perform multilayer decryption
        for (PeerConnection peerConnection : selectedPeers) {
            // Decrypt the current payload
            String decryptedPayload = AES.decrypt(currentPayload, peerConnection.getSessionKey(sessionId));
            System.out.println("Decrypted message: " + decryptedPayload);

            // Convert the decrypted payload to a message object
            Layer layerMessage = (Layer) Util.jsonToMessage(decryptedPayload);
            System.out.println("Layer message: " + layerMessage.getBody() + " with " + layerMessage.getNextPeer() + " and " + layerMessage.getPreviousPeer());

            // Update the current payload to the body of the message
            currentPayload = layerMessage.getBody();
        }
        return currentPayload;

    }

    private void establishSessionKeys() {
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
}
