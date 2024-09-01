public class DiscoveryMessage extends Message {
    private final String body;

    public DiscoveryMessage(String body) {
        super(MessageType.DISCOVERY);
        this.body = body;
    }

    public String getBody() {
        return body;
    }
}
