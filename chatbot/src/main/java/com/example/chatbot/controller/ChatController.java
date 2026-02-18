package com.example.chatbot.controller;

import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestClient;

import java.util.Map;

@RestController
@RequestMapping("/api/chat")
@CrossOrigin(origins = "http://localhost:4200")
public class ChatController {

    private final RestClient restClient = RestClient.create("http://localhost:11434");

    @GetMapping("/ping")
    public String ping() {
        return "OK";
    }

    @PostMapping
    public Map<String, String> chat(@RequestBody Map<String, String> request) {

        String userMessage = request.get("message");

        Map<String, Object> body = Map.of(
                "model", "phi3:latest",
                "prompt", userMessage,
                "stream", false,
                "options", Map.of(
                        "num_predict", 200,          // limits reply length (faster)
                        "temperature", 0.7,
                        "stop", new String[]{"assistant:", "Assistant:", "USER:", "User:"}
                )
        );

        Map response = restClient.post()
                .uri("/api/generate")
                .body(body)
                .retrieve()
                .body(Map.class);

        String reply = (String) response.get("response");

        return Map.of("reply", reply);
    }
}
