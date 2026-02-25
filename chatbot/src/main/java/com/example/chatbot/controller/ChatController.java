package com.example.chatbot.controller;

import com.example.chatbot.model.Conversation;
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

    private final RestClient restClient;
    private final ConversationRepository convoRepo;
    private final UserRepository userRepo;

    public ChatController(RestClient restClient,
                          ConversationRepository convoRepo,
                          UserRepository userRepo) {
        this.restClient = restClient;
        this.convoRepo = convoRepo;
        this.userRepo = userRepo;
    }

    @PostMapping
    public Map<String, String> chat(@RequestBody ChatRequest request) {

        String userMessage = Optional.ofNullable(request.message).orElse("").trim();
        if (userMessage.isEmpty()) return Map.of("reply", "Please type a message.");

        List<Map<String, Object>> messages = new ArrayList<>();

        // ✅ SYSTEM RULES
        messages.add(Map.of(
                "role", "system",
                "content", "You are a helpful assistant. Keep answers clear and respectful."
        ));

        // ✅ USER MEMORY (Name) - only if logged in
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

        boolean hasConvoId = request.conversationId != null && !request.conversationId.isBlank();
        boolean hasHistory = request.history != null && !request.history.isEmpty();

        // ✅ MEMORY SOURCE:
        // - If logged in -> use DB conversation turns
        // - Else (guest) -> use history from frontend
        if (hasConvoId) {
            convoRepo.findById(request.conversationId).ifPresent(convo -> {
                List<Conversation.ChatTurn> turns = convo.getTurns();
                if (turns != null && !turns.isEmpty()) {

                    int N = 10; // last 10 turns only
                    int start = Math.max(0, turns.size() - N);

                    for (int i = start; i < turns.size(); i++) {
                        Conversation.ChatTurn t = turns.get(i);

                        String u = t.getUserMessage() == null ? "" : t.getUserMessage().trim();
                        String a = t.getBotResponse() == null ? "" : t.getBotResponse().trim();

                        if (!u.isEmpty()) messages.add(Map.of("role", "user", "content", u));
                        if (!a.isEmpty()) messages.add(Map.of("role", "assistant", "content", a));
                    }
                }
            });

        } else if (hasHistory) {
            // optional: cap guest history so prompt doesn't grow too much
            int limit = Math.min(request.history.size(), 20); // 20 messages ~= 10 turns
            List<HistoryMsg> tail = request.history.subList(request.history.size() - limit, request.history.size());

            for (HistoryMsg h : tail) {
                if (h == null) continue;

                String role = safeRole(h.role);
                String content = h.content == null ? "" : h.content.trim();
                if (content.isEmpty()) continue;

                if (role.equals("user") || role.equals("assistant") || role.equals("system")) {
                    messages.add(Map.of("role", role, "content", content));
                }
            }
        }

        // ✅ CURRENT MESSAGE
        messages.add(Map.of("role", "user", "content", userMessage));

        Map<String, Object> body = Map.of(
                "model", "llama3:8b",   // ✅ UPDATED MODEL HERE
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

            if (reply == null || reply.trim().isEmpty()) reply = "(No reply from model)";
            return Map.of("reply", reply.trim());

        } catch (ResourceAccessException ex) {
            return Map.of("reply", "⚠️ AI server is offline. Start Ollama using: ollama serve");
        } catch (Exception ex) {
            return Map.of("reply", "⚠️ Server error while generating response.");
        }
    }

    private String safeRole(String role) {
        if (role == null) return "";
        return role.trim().toLowerCase(Locale.ROOT);
    }

    public static class ChatRequest {
        public String message;
        public String userId;
        public String conversationId;

        // ✅ guest memory from frontend
        public List<HistoryMsg> history;
    }

    public static class HistoryMsg {
        public String role;    // "user" | "assistant" | "system"
        public String content; // message text
    }
}