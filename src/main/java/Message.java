public class Message {
    private MessageType header;
    private String body;

    public Message(MessageType header, String body) {
        this.header = header;
        this.body = body;
    }

    public MessageType getHeader() {
        return header;
    }

    public String getBody() {
        return body;
    }
}
