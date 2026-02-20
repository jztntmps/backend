package com.example.chatbot.controller;

import com.example.chatbot.model.Conversation;
import com.example.chatbot.repository.ConversationRepository;
import com.example.chatbot.repository.UserRepository;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;

@RestController
@RequestMapping("/api/conversations")
@CrossOrigin(origins = "http://localhost:4200")
public class ConversationController {

    private final ConversationRepository convoRepo;
    private final UserRepository userRepo;

    public ConversationController(ConversationRepository convoRepo, UserRepository userRepo) {
        this.convoRepo = convoRepo;
        this.userRepo = userRepo;
    }

    @PostMapping
    public Conversation create(@RequestBody CreateConversationRequest req) {
        if (req.userId == null || req.userId.trim().isEmpty()) {
            throw new RuntimeException("userId is required");
        }

        userRepo.findById(req.userId)
                .orElseThrow(() -> new RuntimeException("User not found: " + req.userId));

        Conversation c = new Conversation();
        c.setUserId(req.userId);
        c.setStatus("open");
        c.setArchived(false);
        c.setCreatedAt(Instant.now());

        String first = (req.firstUserMessage == null) ? "" : req.firstUserMessage.trim();
        c.setTitle(first.length() > 60 ? first.substring(0, 60) : first);

        c.getTurns().add(new Conversation.ChatTurn(first, req.firstBotResponse));
        return convoRepo.save(c);
    }

    @PostMapping("/{conversationId}/turns")
    public Conversation addTurn(
            @PathVariable("conversationId") String conversationId,
            @RequestBody AddTurnRequest req
    ) {
        Conversation c = convoRepo.findById(conversationId)
                .orElseThrow(() -> new RuntimeException("Conversation not found"));

        c.getTurns().add(new Conversation.ChatTurn(req.userMessage, req.botResponse));
        return convoRepo.save(c);
    }

    @GetMapping("/{conversationId}")
    public Conversation getOne(@PathVariable("conversationId") String conversationId) {
        return convoRepo.findById(conversationId)
                .orElseThrow(() -> new RuntimeException("Conversation not found"));
    }

    @GetMapping("/by-user/{userId}")
    public List<Conversation> getByUser(@PathVariable("userId") String userId) {
        return convoRepo.findByUserIdOrderByCreatedAtDesc(userId);
    }

    @PatchMapping("/{conversationId}/end")
    public Conversation end(@PathVariable("conversationId") String conversationId) {
        Conversation c = convoRepo.findById(conversationId)
                .orElseThrow(() -> new RuntimeException("Conversation not found"));

        c.setStatus("ended");
        return convoRepo.save(c);
    }

    @PatchMapping("/{conversationId}/archive")
    public Conversation archive(@PathVariable("conversationId") String conversationId) {
        Conversation c = convoRepo.findById(conversationId)
                .orElseThrow(() -> new RuntimeException("Conversation not found"));

        c.setArchived(true);
        return convoRepo.save(c);
    }

    @PatchMapping("/{conversationId}/unarchive")
    public Conversation unarchive(@PathVariable("conversationId") String conversationId) {
        Conversation c = convoRepo.findById(conversationId)
                .orElseThrow(() -> new RuntimeException("Conversation not found"));

        c.setArchived(false);
        return convoRepo.save(c);
    }

    @DeleteMapping("/{conversationId}")
    public void delete(@PathVariable("conversationId") String conversationId) {
        if (!convoRepo.existsById(conversationId)) {
            throw new RuntimeException("Conversation not found");
        }
        convoRepo.deleteById(conversationId);
    }

    public static class CreateConversationRequest {
        public String userId;
        public String firstUserMessage;
        public String firstBotResponse;
    }

    public static class AddTurnRequest {
        public String userMessage;
        public String botResponse;
    }
}
