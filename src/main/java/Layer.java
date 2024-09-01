public class Layer extends Message {
    private final String body;
    private final String nextPeer;
    private final String previousPeer;

    public Layer(String body, String nextPeer, String previousPeer) {
        super(MessageType.LAYER);
        this.body = body;
        this.nextPeer = nextPeer;
        this.previousPeer = previousPeer;
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
