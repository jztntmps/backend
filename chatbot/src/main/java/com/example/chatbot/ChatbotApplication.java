package com.example.chatbot;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

@SpringBootApplication
public class ChatbotApplication {

	public static void main(String[] args) {
		SpringApplication.run(ChatbotApplication.class, args);
	}

	@Bean
	CommandLineRunner showMongoUri(@Value("${spring.data.mongodb.uri}") String uri) {
		return args -> {
			System.out.println("=================================");
			System.out.println("MONGO URI BEING USED: " + uri);
			System.out.println("=================================");
		};
	}
}
