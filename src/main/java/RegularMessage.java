public class RegularMessage extends Message {
    private final int sessionId;
    private final String body;
    private final String nextPeer;
    private final String previousPeer;

    public RegularMessage(int sessionId, String body, String nextPeer, String previousPeer) {
        super(MessageType.REGULAR);
        this.sessionId = sessionId;
        this.body = body;
        this.nextPeer = nextPeer;
        this.previousPeer = previousPeer;
    }

    public int getSessionId() {
        return sessionId;
    }

    public String getBody() {
        return body;
    }

    public String getNextPeer() {
        return nextPeer;
    }

    public String getPreviousPeer() {
        return previousPeer;
    }
}
