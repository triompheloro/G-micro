package com.example.projet.data;

import java.util.ArrayList;
import java.util.List;

public class ConversationBuffer {
    private static final int DEFAULT_MAX_SIZE = 10;
    private final List<Message> messages;
    private final int maxSize;

    public ConversationBuffer() {
        this(DEFAULT_MAX_SIZE);
    }

    public ConversationBuffer(int maxSize) {
        this.maxSize = maxSize;
        this.messages = new ArrayList<>();
    }

    public void addMessage(Message message) {
        if (messages.size() >= maxSize) {
            messages.remove(0);  // Supprime le plus ancien message si buffer plein
        }
        messages.add(message);
    }

    public List<Message> getMessages() {
        return new ArrayList<>(messages);
    }

    public void clear() {
        messages.clear();
    }

    public String getContextForAI() {
        StringBuilder context = new StringBuilder();
        for (Message message : messages) {
            context.append(message.getRole())
                  .append(": ")
                  .append(message.getContent())
                  .append("\n");
        }
        return context.toString();
    }
}