import com.google.gson.*;
import java.lang.reflect.Type;

class MessageAdapter implements JsonSerializer<Message>, JsonDeserializer<Message> {
    @Override
    public JsonElement serialize(Message src, Type typeOfSrc, JsonSerializationContext context) {
        JsonObject result = new JsonObject();
        result.addProperty("type", src.getType().name());
        // Serializing the object itself into the "data" field
        JsonObject data = context.serialize(src, src.getClass()).getAsJsonObject();
        result.add("data", data);
        return result;
    }

    @Override
    public Message deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context)
            throws JsonParseException {
        JsonObject jsonObject = json.getAsJsonObject();
        String type = jsonObject.get("type").getAsString();

        MessageType messageType = MessageType.valueOf(type);

        switch (messageType) {
            case LAYER:
                return context.deserialize(jsonObject, Layer.class);
            case FORWARD_MESSAGE:
                return context.deserialize(jsonObject, ForwardMessage.class);
            case BACKWARD_MESSAGE:
                return context.deserialize(jsonObject, BackwardMessage.class);
            case SESSION_KEY_ESTABLISHMENT:
                return context.deserialize(jsonObject, SessionKeyEstablishmentMessage.class);
            case DISCOVERY:
                return context.deserialize(jsonObject, DiscoveryMessage.class);
            case SYMMETRIC_KEY_ESTABLISHMENT:
                System.out.println("This case should work...");
                return context.deserialize(jsonObject, SymmetricKeyEstablishmentMessage.class);
            default:
                throw new JsonParseException("Unknown element type: " + type);
        }
    }
}
