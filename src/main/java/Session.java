import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.crypto.SecretKey;
import java.math.BigInteger;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.security.SecureRandom;
import java.util.Base64;

public class Session {
    private static final Logger logger = LogManager.getLogger();

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

        logger.info("Encrypting message with layers");

        String currentPayload = text;
        // Perform multilayer encryption
        for (int i = selectedPeers.length - 1; i >= 0; i--) {
            PeerConnection peerConnection = selectedPeers[i];

            try {
                // Prepare routing information
                String nextPeer = (i < selectedPeers.length - 1) ? selectedPeers[i + 1].getIp() : null;
                String previousPeer = (i > 0) ? selectedPeers[i - 1].getIp() : InetAddress.getLocalHost().getHostAddress(); // change null to your IP

                // Create a new message with the current payload and routing info
                Layer layerMessage = new Layer(currentPayload, nextPeer, previousPeer);
                logger.info("Layer: body={}, nextPeer={}, previousPeer={}", layerMessage.getBody(), layerMessage.getNextPeer(), layerMessage.getPreviousPeer());
                String jsonLayerMessage = Util.messageToJson(layerMessage);

                logger.info("Encrypt layer with sessionKey={}", peerConnection.getSessionKey(sessionId));
                currentPayload = AES.encrypt(jsonLayerMessage, peerConnection.getSessionKey(sessionId));
            } catch (UnknownHostException e) {
                System.out.println("Cannot get local host address: " + e);
            }
        }
        return currentPayload;
    }

    public String decryptWithLayers(String encrypted) {
        logger.info("Decrypting message with layers");

        String currentPayload = encrypted;
        // Perform multilayer decryption
        for (PeerConnection peerConnection : selectedPeers) {
            // Decrypt the current payload
            logger.info("Decrypt layer with sessionKey={}", peerConnection.getSessionKey(sessionId));
            String decryptedPayload = AES.decrypt(currentPayload, peerConnection.getSessionKey(sessionId));

            // Convert the decrypted payload to a message object
            Layer layerMessage = (Layer) Util.jsonToMessage(decryptedPayload);
            logger.info("Layer: body={}", layerMessage.getBody());

            // Update the current payload to the body of the message
            currentPayload = layerMessage.getBody();
        }
        return currentPayload;

    }

    private void establishSessionKeys() {
        logger.info("Establishing and sending session keys to all peers in the chain");
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
