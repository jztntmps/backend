package com.example.chatbot.controller;

import com.example.chatbot.model.Conversation;
import com.example.chatbot.repository.ConversationRepository;
import com.example.chatbot.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ConversationControllerTest {

    @Mock private ConversationRepository convoRepo;
    @Mock private UserRepository userRepo;

    private ConversationController controller;

    @BeforeEach
    void setUp() {
        controller = new ConversationController(convoRepo, userRepo);
    }

    private static ConversationController.CreateConversationRequest createReq(
            String userId, String firstUserMessage, String firstBotResponse
    ) {
        ConversationController.CreateConversationRequest r =
                new ConversationController.CreateConversationRequest();
        r.userId = userId;
        r.firstUserMessage = firstUserMessage;
        r.firstBotResponse = firstBotResponse;
        return r;
    }

    private static ConversationController.AddTurnRequest addTurnReq(String u, String b) {
        ConversationController.AddTurnRequest r = new ConversationController.AddTurnRequest();
        r.userMessage = u;
        r.botResponse = b;
        return r;
    }

    // -------------------------
    // create()
    // -------------------------

    @Test
    void create_whenUserIdNull_throws() {
        RuntimeException ex = assertThrows(RuntimeException.class, () ->
                controller.create(createReq(null, "hi", "bot"))
        );
        assertEquals("userId is required", ex.getMessage());
        verifyNoInteractions(userRepo, convoRepo);
    }

    @Test
    void create_whenUserIdBlank_throws() {
        RuntimeException ex = assertThrows(RuntimeException.class, () ->
                controller.create(createReq("   ", "hi", "bot"))
        );
        assertEquals("userId is required", ex.getMessage());
        verifyNoInteractions(userRepo, convoRepo);
    }

    @Test
    void create_whenUserNotFound_throws() {
        when(userRepo.findById("u1")).thenReturn(Optional.empty());

        RuntimeException ex = assertThrows(RuntimeException.class, () ->
                controller.create(createReq("u1", "hi", "bot"))
        );

        assertEquals("User not found: u1", ex.getMessage());
        verify(userRepo).findById("u1");
        verifyNoInteractions(convoRepo);
    }

    @Test
    void create_success_trimsTitle_under60Chars() {
        // user exists
        when(userRepo.findById("u1")).thenReturn(Optional.of(mock(com.example.chatbot.model.User.class)));

        // save returns the same object passed
        when(convoRepo.save(any(Conversation.class))).thenAnswer(inv -> inv.getArgument(0));

        Conversation out = controller.create(createReq("u1", "   hello world   ", "hi human"));

        assertEquals("u1", out.getUserId());
        assertEquals("open", out.getStatus());
        assertFalse(out.isArchived());
        assertNotNull(out.getCreatedAt());

        // title should be trimmed and not cut
        assertEquals("hello world", out.getTitle());

        // first turn should exist
        assertNotNull(out.getTurns());
        assertEquals(1, out.getTurns().size());
        assertEquals("hello world", out.getTurns().get(0).getUserMessage());
        assertEquals("hi human", out.getTurns().get(0).getBotResponse());

        verify(userRepo).findById("u1");
        verify(convoRepo).save(any(Conversation.class));
    }

    @Test
    void create_success_titleOver60Chars_isSubstringedTo60() {
        when(userRepo.findById("u1")).thenReturn(Optional.of(mock(com.example.chatbot.model.User.class)));
        when(convoRepo.save(any(Conversation.class))).thenAnswer(inv -> inv.getArgument(0));

        String longMsg = "a".repeat(80); // 80 chars
        Conversation out = controller.create(createReq("u1", longMsg, "bot"));

        assertEquals(60, out.getTitle().length());
        assertEquals(longMsg.substring(0, 60), out.getTitle());
    }

    @Test
    void create_whenFirstUserMessageNull_titleBecomesEmpty() {
        when(userRepo.findById("u1")).thenReturn(Optional.of(mock(com.example.chatbot.model.User.class)));
        when(convoRepo.save(any(Conversation.class))).thenAnswer(inv -> inv.getArgument(0));

        Conversation out = controller.create(createReq("u1", null, "bot"));

        assertEquals("", out.getTitle());
        assertEquals("", out.getTurns().get(0).getUserMessage());
    }

    // -------------------------
    // addTurn()
    // -------------------------

    @Test
    void addTurn_whenConversationNotFound_throws() {
        when(convoRepo.findById("c1")).thenReturn(Optional.empty());

        RuntimeException ex = assertThrows(RuntimeException.class, () ->
                controller.addTurn("c1", addTurnReq("u", "b"))
        );

        assertEquals("Conversation not found", ex.getMessage());
        verify(convoRepo).findById("c1");
        verify(convoRepo, never()).save(any());
    }

    @Test
    void addTurn_success_addsTurnAndSaves() {
        Conversation convo = new Conversation();
        convo.setTurns(new ArrayList<>());

        when(convoRepo.findById("c1")).thenReturn(Optional.of(convo));
        when(convoRepo.save(any(Conversation.class))).thenAnswer(inv -> inv.getArgument(0));

        Conversation out = controller.addTurn("c1", addTurnReq("hello", "hi"));

        assertEquals(1, out.getTurns().size());
        assertEquals("hello", out.getTurns().get(0).getUserMessage());
        assertEquals("hi", out.getTurns().get(0).getBotResponse());

        verify(convoRepo).findById("c1");
        verify(convoRepo).save(convo);
    }

    // -------------------------
    // getOne()
    // -------------------------

    @Test
    void getOne_whenNotFound_throws() {
        when(convoRepo.findById("c1")).thenReturn(Optional.empty());

        RuntimeException ex = assertThrows(RuntimeException.class, () ->
                controller.getOne("c1")
        );

        assertEquals("Conversation not found", ex.getMessage());
    }

    @Test
    void getOne_success_returnsConversation() {
        Conversation convo = new Conversation();
        when(convoRepo.findById("c1")).thenReturn(Optional.of(convo));

        Conversation out = controller.getOne("c1");

        assertSame(convo, out);
    }

    // -------------------------
    // getByUser()
    // -------------------------

    @Test
    void getByUser_returnsListFromRepo() {
        List<Conversation> list = List.of(new Conversation(), new Conversation());
        when(convoRepo.findByUserIdOrderByCreatedAtDesc("u1")).thenReturn(list);

        List<Conversation> out = controller.getByUser("u1");

        assertSame(list, out);
        verify(convoRepo).findByUserIdOrderByCreatedAtDesc("u1");
    }

    // -------------------------
    // end/archive/unarchive
    // -------------------------

    @Test
    void end_whenNotFound_throws() {
        when(convoRepo.findById("c1")).thenReturn(Optional.empty());

        RuntimeException ex = assertThrows(RuntimeException.class, () -> controller.end("c1"));
        assertEquals("Conversation not found", ex.getMessage());
    }

    @Test
    void end_success_setsEndedAndSaves() {
        Conversation convo = new Conversation();
        when(convoRepo.findById("c1")).thenReturn(Optional.of(convo));
        when(convoRepo.save(any(Conversation.class))).thenAnswer(inv -> inv.getArgument(0));

        Conversation out = controller.end("c1");

        assertEquals("ended", out.getStatus());
        verify(convoRepo).save(convo);
    }

    @Test
    void archive_success_setsArchivedTrueAndSaves() {
        Conversation convo = new Conversation();
        when(convoRepo.findById("c1")).thenReturn(Optional.of(convo));
        when(convoRepo.save(any(Conversation.class))).thenAnswer(inv -> inv.getArgument(0));

        Conversation out = controller.archive("c1");

        assertTrue(out.isArchived());
        verify(convoRepo).save(convo);
    }

    @Test
    void unarchive_success_setsArchivedFalseAndSaves() {
        Conversation convo = new Conversation();
        convo.setArchived(true);
        when(convoRepo.findById("c1")).thenReturn(Optional.of(convo));
        when(convoRepo.save(any(Conversation.class))).thenAnswer(inv -> inv.getArgument(0));

        Conversation out = controller.unarchive("c1");

        assertFalse(out.isArchived());
        verify(convoRepo).save(convo);
    }

    @Test
    void archive_whenNotFound_throws() {
        when(convoRepo.findById("c1")).thenReturn(Optional.empty());
        RuntimeException ex = assertThrows(RuntimeException.class, () -> controller.archive("c1"));
        assertEquals("Conversation not found", ex.getMessage());
    }

    @Test
    void unarchive_whenNotFound_throws() {
        when(convoRepo.findById("c1")).thenReturn(Optional.empty());
        RuntimeException ex = assertThrows(RuntimeException.class, () -> controller.unarchive("c1"));
        assertEquals("Conversation not found", ex.getMessage());
    }

    // -------------------------
    // delete()
    // -------------------------

    @Test
    void delete_whenNotExists_throws() {
        when(convoRepo.existsById("c1")).thenReturn(false);

        RuntimeException ex = assertThrows(RuntimeException.class, () ->
                controller.delete("c1")
        );

        assertEquals("Conversation not found", ex.getMessage());
        verify(convoRepo).existsById("c1");
        verify(convoRepo, never()).deleteById(anyString());
    }

    @Test
    void delete_whenExists_deletes() {
        when(convoRepo.existsById("c1")).thenReturn(true);

        controller.delete("c1");

        verify(convoRepo).existsById("c1");
        verify(convoRepo).deleteById("c1");
    }
}