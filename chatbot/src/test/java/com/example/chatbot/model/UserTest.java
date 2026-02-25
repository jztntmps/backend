package com.example.chatbot.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class UserTest {

    @Test
    void testDefaultConstructor() {
        User user = new User();

        assertNull(user.getId());
        assertNull(user.getUsername());
        assertNull(user.getEmail());
        assertNull(user.getPassword());
    }

    @Test
    void testParameterizedConstructor() {
        User user = new User("john", "john@example.com", "password123");

        assertEquals("john", user.getUsername());
        assertEquals("john@example.com", user.getEmail());
        assertEquals("password123", user.getPassword());
        assertNull(user.getId()); // id not set in constructor
    }

    @Test
    void testSetId() {
        User user = new User();
        user.setId("123");

        assertEquals("123", user.getId());
    }

    @Test
    void testSetUsername() {
        User user = new User();
        user.setUsername("alice");

        assertEquals("alice", user.getUsername());
    }

    @Test
    void testSetEmail() {
        User user = new User();
        user.setEmail("alice@example.com");

        assertEquals("alice@example.com", user.getEmail());
    }

    @Test
    void testSetPassword() {
        User user = new User();
        user.setPassword("securePass");

        assertEquals("securePass", user.getPassword());
    }


}