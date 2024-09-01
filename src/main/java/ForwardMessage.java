public class ForwardMessage extends Message {
    private final int sessionId;
    private final String body;

    public ForwardMessage(int sessionId, String body) {
        super(MessageType.FORWARD_MESSAGE);
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
