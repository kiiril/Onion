package com.github.kiiril;

import com.github.kiiril.messages.Layer;
import com.github.kiiril.messages.SessionKeyEstablishmentMessage;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import javax.crypto.SecretKey;
import java.math.BigInteger;
import java.security.SecureRandom;
import java.util.*;

import static com.github.kiiril.PeerConnectionManager.HOST_IP;

public class Session {
    private static final Logger logger = LogManager.getLogger();

    private final int sessionId;

    private PeerConnection[] selectedRouters;

    private SecretKey mySessionKey;
    private PeerConnection previousPeer;

    public Session(int sessionId) {
        this.sessionId = sessionId;
    }

    public boolean isSendingPeer() {
        return previousPeer == null && selectedRouters != null;
    }

    public PeerConnection[] selectRouters(int numberOfRouters, Map<String, PeerConnection> activePeerConnections) {
        if (activePeerConnections.size() < numberOfRouters) {
            logger.info("Not enough peers to select from, returning all peers");
            this.selectedRouters = activePeerConnections.values().toArray(new PeerConnection[0]); // return all peers if not enough
            return selectedRouters;
        }

        PeerConnection[] selectedPeers = new PeerConnection[numberOfRouters];

        List<String> allIps = new ArrayList<>(activePeerConnections.keySet());
        Collections.shuffle(allIps);

        for (int i = 0; i < numberOfRouters; i++) {
            selectedPeers[i] = activePeerConnections.get(allIps.get(i));
        }

        logger.info("Routers were selected");

        this.selectedRouters = selectedPeers;
        return selectedRouters;
    }

    public String encryptLayers(String plainText) {
        generateAndSpreadSessionKey();

        logger.info("Encrypting message with layers");

        String currentPayload = plainText;
        for (int i = selectedRouters.length - 1; i >= 0; i--) {
            PeerConnection peerConnection = selectedRouters[i];

            // Prepare routing information
            String nextPeer = (i < selectedRouters.length - 1) ? selectedRouters[i + 1].getIp() : null;
            String previousPeer = (i > 0) ? selectedRouters[i - 1].getIp() : HOST_IP;

            // Create a new layer with the current payload and routing info
            Layer layer = new Layer(currentPayload, nextPeer, previousPeer);
            logger.info("Layer: body={}, nextPeer={}, previousPeer={}", layer.getBody(), layer.getNextPeer(), layer.getPreviousPeer());
            String jsonLayerMessage = Util.messageToJson(layer);

            logger.info("Encrypt layer with sessionKey={}", peerConnection.getSessionKey(sessionId));
            currentPayload = AES.encrypt(jsonLayerMessage, peerConnection.getSessionKey(sessionId));
        }
        return currentPayload;
    }

    public String decryptLayers(String cipherText) {
        logger.info("Decrypting message with layers");

        String currentPayload = cipherText;
        for (PeerConnection peerConnection : selectedRouters) {
            // Decrypt current layer
            logger.info("Decrypt layer with sessionKey={}", peerConnection.getSessionKey(sessionId));
            String decryptedLayer = AES.decrypt(currentPayload, peerConnection.getSessionKey(sessionId));

            Layer layerMessage = (Layer) Util.jsonToMessage(decryptedLayer);
            logger.info("Layer: body={}", layerMessage.getBody());

            currentPayload = layerMessage.getBody();
        }
        return currentPayload;

    }

    private void generateAndSpreadSessionKey() {
        logger.info("Establishing and sending session keys to all peers in the chain");

        for (PeerConnection selectedPeer : selectedRouters) {
            BigInteger randomSecret = new BigInteger(2048, new SecureRandom());
            SecretKey sessionKey = AES.generateKey(randomSecret);

            selectedPeer.addSessionKey(sessionId, sessionKey);

            // Send the session key to all peers in the chain
            SessionKeyEstablishmentMessage message = new SessionKeyEstablishmentMessage(sessionId, Base64.getEncoder().encodeToString(sessionKey.getEncoded()));
            String jsonMessage = Util.messageToJson(message);
            selectedPeer.sendMessage(AES.encrypt(jsonMessage, selectedPeer.getSymmetricKey()));
        }
    }

    public void setMySessionKey(SecretKey mySessionKey) {
        this.mySessionKey = mySessionKey;
    }

    public SecretKey getMySessionKey() {
        return mySessionKey;
    }

    public void setPreviousPeer(PeerConnection previousPeer) {
        this.previousPeer = previousPeer;
    }

    public PeerConnection getPreviousPeer() {
        return previousPeer;
    }

    public int getSessionId() {
        return sessionId;
    }
}
