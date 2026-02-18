package com.example.chatbot.repository;

import org.springframework.data.mongodb.repository.MongoRepository;
import com.example.chatbot.model.User;

public interface UserRepository extends MongoRepository<User, String> {
}
