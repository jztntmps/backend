package com.example.chatbot.controller;

import com.example.chatbot.model.Conversation;
import com.example.chatbot.model.User;
import com.example.chatbot.repository.ConversationRepository;
import com.example.chatbot.repository.UserRepository;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

import java.util.*;

@RestController
@RequestMapping("/api/chat")
@CrossOrigin(origins = "http://localhost:4200")
public class ChatController {

    private final RestClient restClient = RestClient.create("http://localhost:11434");
    private final ConversationRepository convoRepo;
    private final UserRepository userRepo;

    public ChatController(ConversationRepository convoRepo,
                          UserRepository userRepo) {
        this.convoRepo = convoRepo;
        this.userRepo = userRepo;
    }

    @PostMapping
    public Map<String, String> chat(@RequestBody ChatRequest request) {

        String userMessage = Optional.ofNullable(request.message).orElse("").trim();
        if (userMessage.isEmpty())
            return Map.of("reply", "Please type a message.");

        List<Map<String, Object>> messages = new ArrayList<>();

        // ✅ SYSTEM RULES
        messages.add(Map.of(
                "role", "system",
                "content", "You are a helpful assistant. Keep answers clear and respectful."
        ));

        // ✅ LOAD USER MEMORY (Name)
        if (request.userId != null && !request.userId.isBlank()) {
            userRepo.findById(request.userId).ifPresent(user -> {
                if (user.getUsername() != null && !user.getUsername().isBlank()) {
                    messages.add(Map.of(
                            "role", "system",
                            "content", "The user's name is " + user.getUsername() + ". Address them by name when appropriate."
                    ));
                }
            });
        }

        // ✅ LOAD CONVERSATION MEMORY
        if (request.conversationId != null && !request.conversationId.isBlank()) {
            convoRepo.findById(request.conversationId).ifPresent(convo -> {
                List<Conversation.ChatTurn> turns = convo.getTurns();
                if (turns != null && !turns.isEmpty()) {

                    int N = 10; // last 10 turns only
                    int start = Math.max(0, turns.size() - N);

                    for (int i = start; i < turns.size(); i++) {
                        Conversation.ChatTurn t = turns.get(i);

                        if (t.getUserMessage() != null)
                            messages.add(Map.of("role", "user", "content", t.getUserMessage()));

                        if (t.getBotResponse() != null)
                            messages.add(Map.of("role", "assistant", "content", t.getBotResponse()));
                    }
                }
            });
        }

        // ✅ CURRENT MESSAGE
        messages.add(Map.of(
                "role", "user",
                "content", userMessage
        ));

        Map<String, Object> body = Map.of(
                "model", "llama3.2:1b",
                "messages", messages,
                "stream", false
        );

        try {
            Map response = restClient.post()
                    .uri("/api/chat")
                    .body(body)
                    .retrieve()
                    .body(Map.class);

            String reply = null;

            if (response != null && response.get("message") instanceof Map msg) {
                Object content = msg.get("content");
                if (content != null) reply = content.toString();
            }

            if (reply == null || reply.trim().isEmpty())
                reply = "(No reply from model)";

            return Map.of("reply", reply.trim());

        } catch (ResourceAccessException ex) {
            return Map.of("reply",
                    "⚠️ AI server is offline. Start Ollama using: ollama serve");
        } catch (Exception ex) {
            return Map.of("reply", "⚠️ Server error while generating response.");
        }
    }

    public static class ChatRequest {
        public String message;
        public String userId;
        public String conversationId;
    }
}