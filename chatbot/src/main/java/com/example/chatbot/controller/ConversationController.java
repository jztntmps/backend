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

    // ✅ Create a conversation with first message (title auto = first message)
    @PostMapping
    public Conversation create(@RequestBody CreateConversationRequest req) {

        if (req.userId == null || req.userId.trim().isEmpty()) {
            throw new RuntimeException("userId is required");
        }

        // ✅ Manual "foreign key" check
        userRepo.findById(req.userId)
                .orElseThrow(() -> new RuntimeException("User not found: " + req.userId));

        Conversation c = new Conversation();
        c.setUserId(req.userId);
        c.setStatus("open");
        c.setArchivedAt(null);

        // title = first user message (trim + short)
        String first = (req.firstUserMessage == null) ? "" : req.firstUserMessage.trim();
        c.setTitle(first.length() > 60 ? first.substring(0, 60) : first);

        // first chat turn
        c.getTurns().add(new Conversation.ChatTurn(first, req.firstBotResponse));

        return convoRepo.save(c);
    }

    // ✅ Add another turn to existing conversation
    @PostMapping("/{conversationId}/turns")
    public Conversation addTurn(@PathVariable String conversationId, @RequestBody AddTurnRequest req) {
        Conversation c = convoRepo.findById(conversationId)
                .orElseThrow(() -> new RuntimeException("Conversation not found"));

        c.getTurns().add(new Conversation.ChatTurn(req.userMessage, req.botResponse));
        return convoRepo.save(c);
    }

    // ✅ Get a conversation
    @GetMapping("/{conversationId}")
    public Conversation getOne(@PathVariable String conversationId) {
        return convoRepo.findById(conversationId)
                .orElseThrow(() -> new RuntimeException("Conversation not found"));
    }

    // ✅ List conversations by userId (for sidebar/history)
    @GetMapping("/by-user/{userId}")
    public List<Conversation> getByUser(@PathVariable String userId) {
        return convoRepo.findByUserIdOrderByCreatedAtDesc(userId);
    }

    // ✅ End/Archive conversation
    @PatchMapping("/{conversationId}/end")
    public Conversation end(@PathVariable String conversationId) {
        Conversation c = convoRepo.findById(conversationId)
                .orElseThrow(() -> new RuntimeException("Conversation not found"));
        c.setStatus("ended");
        c.setArchivedAt(Instant.now());
        return convoRepo.save(c);
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
