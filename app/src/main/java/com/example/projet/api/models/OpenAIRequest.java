package com.example.projet.api.models;

import com.google.gson.annotations.SerializedName;
import java.util.List;

public class OpenAIRequest {
    @SerializedName("model")
    private String model = "gpt-3.5-turbo";

    @SerializedName("messages")
    private List<Message> messages;

    @SerializedName("temperature")
    private double temperature = 0.7;

    public static class Message {
        @SerializedName("role")
        private String role;

        @SerializedName("content")
        private String content;

        public Message(String role, String content) {
            this.role = role;
            this.content = content;
        }
    }

    public OpenAIRequest(List<Message> messages) {
        this.messages = messages;
    }

    // Getters et Setters
    public String getModel() { return model; }
    public void setModel(String model) { this.model = model; }
    
    public List<Message> getMessages() { return messages; }
    public void setMessages(List<Message> messages) { this.messages = messages; }
    
    public double getTemperature() { return temperature; }
    public void setTemperature(double temperature) { this.temperature = temperature; }
}