package com.example.projet.data;

import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "messages")
public class Message {
    @PrimaryKey(autoGenerate = true)
    private int id;
    private String role;        // "user" ou "assistant"
    private String content;     // contenu du message
    private long timestamp;     // horodatage
    private boolean isDetectionRequest; // indique si c'est une demande de détection

    public Message(String role, String content) {
        this.role = role;
        this.content = content;
        this.timestamp = System.currentTimeMillis();
        this.isDetectionRequest = false;
    }

    // Getters et Setters
    public int getId() { return id; }
    public void setId(int id) { this.id = id; }
    
    public String getRole() { return role; }
    public void setRole(String role) { this.role = role; }
    
    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }
    
    public long getTimestamp() { return timestamp; }
    public void setTimestamp(long timestamp) { this.timestamp = timestamp; }
    
    public boolean isDetectionRequest() { return isDetectionRequest; }
    public void setDetectionRequest(boolean detectionRequest) { isDetectionRequest = detectionRequest; }
}