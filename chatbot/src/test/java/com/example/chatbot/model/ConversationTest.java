package com.example.chatbot.model;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ConversationTest {

    @Test
    void testDefaultValues() {
        Conversation conversation = new Conversation();

        assertFalse(conversation.isArchived(), "Archived should be false by default");
        assertNotNull(conversation.getTurns(), "Turns list should be initialized");
        assertNotNull(conversation.getCreatedAt(), "CreatedAt should be initialized");
    }

    @Test
    void testSettersAndGetters() {
        Conversation conversation = new Conversation();

        conversation.setConversationId("conv1");
        conversation.setUserId("user1");
        conversation.setTitle("Test Conversation");
        conversation.setStatus("open");
        conversation.setArchived(true);

        assertEquals("conv1", conversation.getConversationId());
        assertEquals("user1", conversation.getUserId());
        assertEquals("Test Conversation", conversation.getTitle());
        assertEquals("open", conversation.getStatus());
        assertTrue(conversation.isArchived());
    }

    @Test
    void testChatTurns() {
        Conversation conversation = new Conversation();

        Conversation.ChatTurn turn1 = new Conversation.ChatTurn("Hello", "Hi there!");
        Conversation.ChatTurn turn2 = new Conversation.ChatTurn("How are you?", "I'm good!");

        List<Conversation.ChatTurn> turns = new ArrayList<>();
        turns.add(turn1);
        turns.add(turn2);

        conversation.setTurns(turns);

        assertEquals(2, conversation.getTurns().size());
        assertEquals("Hello", conversation.getTurns().get(0).getUserMessage());
        assertEquals("Hi there!", conversation.getTurns().get(0).getBotResponse());
    }

    @Test
    void testAddChatTurn() {
        Conversation conversation = new Conversation();

        Conversation.ChatTurn turn = new Conversation.ChatTurn("Hi", "Hello!");
        conversation.getTurns().add(turn);

        assertEquals(1, conversation.getTurns().size());
        assertEquals("Hi", conversation.getTurns().get(0).getUserMessage());
    }

    @Test
    void testChatTurnCreatedAt() {
        Conversation.ChatTurn turn = new Conversation.ChatTurn("Hello", "Hi");
        assertNotNull(turn.getCreatedAt());
        assertTrue(turn.getCreatedAt() instanceof Instant);
    }
    @Test
    void testChatTurnDefaultConstructor() {
        Conversation.ChatTurn turn = new Conversation.ChatTurn();

        assertNotNull(turn.getCreatedAt(), "createdAt should be initialized");
        assertNull(turn.getUserMessage(), "userMessage should be null by default");
        assertNull(turn.getBotResponse(), "botResponse should be null by default");
    }

    @Test
    void testSetUserMessage() {
        Conversation.ChatTurn turn = new Conversation.ChatTurn();
        turn.setUserMessage("Test message");

        assertEquals("Test message", turn.getUserMessage());
    }

    @Test
    void testSetBotResponse() {
        Conversation.ChatTurn turn = new Conversation.ChatTurn();
        turn.setBotResponse("Bot reply");

        assertEquals("Bot reply", turn.getBotResponse());
    }

    @Test
    void testSetCreatedAt() {
        Conversation.ChatTurn turn = new Conversation.ChatTurn();
        Instant customTime = Instant.parse("2024-01-01T00:00:00Z");

        turn.setCreatedAt(customTime);

        assertEquals(customTime, turn.getCreatedAt());
    }
    @Test
    void testConversationSetCreatedAt() {
        Conversation conversation = new Conversation();

        Instant customTime = Instant.parse("2023-12-01T12:00:00Z");
        conversation.setCreatedAt(customTime);

        assertEquals(customTime, conversation.getCreatedAt());
    }

}