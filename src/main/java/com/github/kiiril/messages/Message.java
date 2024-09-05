package com.github.kiiril.messages;

import com.github.kiiril.MessageType;

public abstract class Message {
    private final MessageType type;

    public Message(MessageType type) {
        this.type = type;
    }

    public MessageType getType() {
        return type;
    }
}
