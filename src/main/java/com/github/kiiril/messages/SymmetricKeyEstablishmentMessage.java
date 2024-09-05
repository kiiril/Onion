package com.github.kiiril.messages;

import com.github.kiiril.MessageType;

public class SymmetricKeyEstablishmentMessage extends Message {
    private final String body;

    public SymmetricKeyEstablishmentMessage(String body) {
        super(MessageType.SYMMETRIC_KEY_ESTABLISHMENT);
        this.body = body;
    }

    public String getBody() {
        return body;
    }
}
