package com.example.chatbot.controller;

import com.example.chatbot.model.User;
import com.example.chatbot.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserControllerTest {

    @Mock private UserRepository userRepository;

    private UserController controller;

    @BeforeEach
    void setUp() {
        controller = new UserController(userRepository);
    }

    @Test
    void saveUser_callsRepoSave_andReturnsSavedUser() {
        User input = new User();
        input.setUsername("john");
        input.setEmail("john@example.com");
        input.setPassword("pw");

        when(userRepository.save(input)).thenReturn(input);

        User out = controller.saveUser(input);

        assertSame(input, out);
        verify(userRepository).save(input);
        verifyNoMoreInteractions(userRepository);
    }
}