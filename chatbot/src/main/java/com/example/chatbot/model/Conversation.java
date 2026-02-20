package com.example.chatbot.model;

import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Document(collection = "conversations")
public class Conversation {

    @Id
    private String conversationId;

    private String userId;
    private String title;
    private String status; // "open" or "ended"

    private boolean archived = false;

    private List<ChatTurn> turns = new ArrayList<>();

    @CreatedDate
    private Instant createdAt = Instant.now();

    public Conversation() {}

    public String getConversationId() { return conversationId; }
    public void setConversationId(String conversationId) { this.conversationId = conversationId; }

    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public boolean isArchived() { return archived; }
    public void setArchived(boolean archived) { this.archived = archived; }

    public List<ChatTurn> getTurns() { return turns; }
    public void setTurns(List<ChatTurn> turns) { this.turns = turns; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public static class ChatTurn {
        private String userMessage;
        private String botResponse;
        private Instant createdAt;

        public ChatTurn() {
            this.createdAt = Instant.now();
        }

        public ChatTurn(String userMessage, String botResponse) {
            this.userMessage = userMessage;
            this.botResponse = botResponse;
            this.createdAt = Instant.now();
        }

        public String getUserMessage() { return userMessage; }
        public void setUserMessage(String userMessage) { this.userMessage = userMessage; }

        public String getBotResponse() { return botResponse; }
        public void setBotResponse(String botResponse) { this.botResponse = botResponse; }

        public Instant getCreatedAt() { return createdAt; }
        public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    }
}
