public class SessionKeyEstablishmentMessage extends Message {
    private final int sessionId;
    private final String sessionKey;


    public SessionKeyEstablishmentMessage(int sessionId, String sessionKey) {
        super(MessageType.SESSION_KEY_ESTABLISHMENT);
        this.sessionId = sessionId;
        this.sessionKey = sessionKey;
    }

    public int getSessionId() {
        return sessionId;
    }

    public String getSessionKey() {
        return sessionKey;
    }
}
