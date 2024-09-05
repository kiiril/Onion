package com.github.kiiril;

import com.github.kiiril.messages.Message;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.lang.reflect.Type;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Scanner;
import java.util.Set;

public class Util {
    private static final Logger logger = LogManager.getLogger();

    private static final Gson gson = new GsonBuilder()
            .registerTypeAdapter(Message.class, new MessageAdapter())
            .create();

    public static String messageToJson(Message message) {
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

    public static String makeRequest(String stringUrl) {
        logger.info("Making request to: {}", stringUrl);
        try {
            URL url = new URL(stringUrl);
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("GET");

            int responseCode = connection.getResponseCode();
            if (responseCode == HttpURLConnection.HTTP_OK) {
                logger.info("Status code is OK");
                Scanner scanner = new Scanner(connection.getInputStream());
                StringBuilder response = new StringBuilder();
                while (scanner.hasNextLine()) {
                    response.append(scanner.nextLine());
                }
                scanner.close();
                return response.toString();
            } else {
                logger.warn("Cannot get response from URL: {}", stringUrl);
                return "";
            }
        } catch (MalformedURLException e) {
            logger.warn("Cannot create URL from string: {} (error={})", stringUrl, e);
        } catch (IOException e) {
            logger.warn("Cannot open connection to URL: {} (error={})", stringUrl, e);
        }
        return "";
    }
}
