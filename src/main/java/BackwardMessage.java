public class BackwardMessage extends Message {
    private final int sessionId;
    private final String body;

    public BackwardMessage(int sessionId, String body) {
        super(MessageType.BACKWARD_MESSAGE);
        this.sessionId = sessionId;
        this.body = body;
    }

    public String getBody() {
        return body;
    }

    public int getSessionId() {
        return sessionId;
    }
}
