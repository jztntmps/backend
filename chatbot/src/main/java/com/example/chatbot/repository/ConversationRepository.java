package com.example.chatbot.repository;

import com.example.chatbot.model.Conversation;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;

public interface ConversationRepository extends MongoRepository<Conversation, String> {
    List<Conversation> findByUserIdOrderByCreatedAtDesc(String userId);
}


