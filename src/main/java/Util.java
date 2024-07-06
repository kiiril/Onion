import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.lang.reflect.Type;
import java.util.Set;

public class Util {
    private static final Gson gson = new Gson();
    public static String messageToJson(MessageType type, String msg) {
        Message message = new Message(type, msg);
        return gson.toJson(message);
    }

    public static Message jsonToMessage(String json) {
        return gson.fromJson(json, Message.class);
    }

    public static String setToString(Set<String> set) {
        return gson.toJson(set);
    }

    public static Set<String> stringToSet(String string) {
        Type setType = new TypeToken<Set<String>>() {}.getType();
        return gson.fromJson(string, setType);
    }
}
