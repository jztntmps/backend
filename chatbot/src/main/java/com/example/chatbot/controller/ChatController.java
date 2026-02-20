package com.example.chatbot.controller;

import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

import java.util.Map;

@RestController
@RequestMapping("/api/chat")
public class ChatController {

    private final RestClient restClient = RestClient.create("http://localhost:11434");

    @PostMapping
    public Map<String, String> chat(@RequestBody Map<String, String> request) {
        String userMessage = request.getOrDefault("message", "").trim();
        if (userMessage.isEmpty()) {
            return Map.of("reply", "Please type a message.");
        }

        Map<String, Object> body = Map.of(
                "model", "llama3.2:1b",
                "prompt", userMessage,
                "stream", false,
                "options", Map.of(
                        "num_predict", 200,
                        "temperature", 0.7
                )
        );

        try {
            Map response = restClient.post()
                    .uri("/api/generate")
                    .body(body)
                    .retrieve()
                    .body(Map.class);

            String reply = response != null ? (String) response.get("response") : null;
            if (reply == null || reply.trim().isEmpty()) {
                reply = "(No reply from model)";
            }
            return Map.of("reply", reply.trim());

        } catch (ResourceAccessException ex) {
            // ✅ Ollama unreachable/offline -> NO 500, just return a friendly reply
            return Map.of("reply",
                    "⚠️ AI server is offline. Please start Ollama (ollama serve) then try again."
            );
        } catch (Exception ex) {
            return Map.of("reply", "⚠️ Server error while generating response.");
        }
    }
}