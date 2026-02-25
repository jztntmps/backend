package com.example.chatbot.controller;

import com.example.chatbot.model.Conversation;
import com.example.chatbot.model.User;
import com.example.chatbot.repository.ConversationRepository;
import com.example.chatbot.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ChatControllerTest {

    @Mock private RestClient restClient;
    @Mock private ConversationRepository convoRepo;
    @Mock private UserRepository userRepo;

    @Mock private RestClient.RequestBodyUriSpec postSpec;
    @Mock private RestClient.RequestBodySpec bodySpec;
    @Mock private RestClient.ResponseSpec responseSpec;

    private ChatController controller;

    @BeforeEach
    void setUp() {
        controller = new ChatController(restClient, convoRepo, userRepo);
    }

    private void stubChain() {
        when(restClient.post()).thenReturn(postSpec);
        when(postSpec.uri(anyString())).thenReturn(bodySpec);

        // Spring version where body(...) returns RequestBodySpec
        when(bodySpec.body(any(Object.class))).thenReturn(bodySpec);

        when(bodySpec.retrieve()).thenReturn(responseSpec);
    }

    private ChatController.ChatRequest req(String msg) {
        ChatController.ChatRequest r = new ChatController.ChatRequest();
        r.message = msg;
        return r;
    }

    private static Map<String, Object> ollamaChatReply(String content) {
        return Map.of("message", Map.of("role", "assistant", "content", content));
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> extractMessagesFromCapturedBody(Object capturedBody) {
        assertTrue(capturedBody instanceof Map, "Expected request body to be a Map");
        Map<String, Object> body = (Map<String, Object>) capturedBody;

        Object msgs = body.get("messages");
        assertTrue(msgs instanceof List, "Expected body.messages to be a List");
        return (List<Map<String, Object>>) msgs;
    }

    @Test
    void chat_whenMessageEmpty_returnsPleaseType() {
        Map<String, String> out = controller.chat(req("   "));
        assertEquals("Please type a message.", out.get("reply"));
        verifyNoInteractions(restClient, convoRepo, userRepo);
    }

    @Test
    void chat_whenOllamaReturnsReply_extractsContent() {
        stubChain();
        when(responseSpec.body(Map.class)).thenReturn(ollamaChatReply("Hi there!"));

        Map<String, String> out = controller.chat(req("hello"));

        assertEquals("Hi there!", out.get("reply"));
    }

    @Test
    void chat_whenOllamaReturnsBlankReply_returnsNoReplyFallback() {
        stubChain();
        when(responseSpec.body(Map.class)).thenReturn(ollamaChatReply("   "));

        Map<String, String> out = controller.chat(req("hello"));

        assertEquals("(No reply from model)", out.get("reply"));
    }

    @Test
    void chat_whenOllamaOffline_returnsOfflineMessage() {
        stubChain();
        when(responseSpec.body(Map.class)).thenThrow(new ResourceAccessException("Connection refused"));

        Map<String, String> out = controller.chat(req("hello"));

        assertEquals("⚠️ AI server is offline. Start Ollama using: ollama serve", out.get("reply"));
    }

    @Test
    void chat_whenUnexpectedError_returnsGenericServerErrorMessage() {
        stubChain();
        when(responseSpec.body(Map.class)).thenThrow(new RuntimeException("boom"));

        Map<String, String> out = controller.chat(req("hello"));

        assertEquals("⚠️ Server error while generating response.", out.get("reply"));
    }

    @Test
    void chat_withConversationId_loadsTurnsFromDb_returnsOk() {
        Conversation convo = new Conversation();
        List<Conversation.ChatTurn> turns = new ArrayList<>();

        Conversation.ChatTurn t1 = new Conversation.ChatTurn();
        t1.setUserMessage("User says 1");
        t1.setBotResponse("Bot says 1");
        turns.add(t1);

        Conversation.ChatTurn t2 = new Conversation.ChatTurn();
        t2.setUserMessage("User says 2");
        t2.setBotResponse("Bot says 2");
        turns.add(t2);

        convo.setTurns(turns);

        when(convoRepo.findById("c1")).thenReturn(Optional.of(convo));

        stubChain();
        when(responseSpec.body(Map.class)).thenReturn(ollamaChatReply("ok"));

        ChatController.ChatRequest r = req("new message");
        r.conversationId = "c1";

        Map<String, String> out = controller.chat(r);

        assertEquals("ok", out.get("reply"));
        verify(convoRepo).findById("c1");
    }

    // ✅ NEW TEST 1: covers userId -> userRepo.findById -> username injected as system message
    @Test
    void chat_whenLoggedInUser_injectsUsernameSystemMessage() {
        stubChain();
        when(responseSpec.body(Map.class)).thenReturn(ollamaChatReply("ok"));

        User user = mock(User.class);
        when(user.getUsername()).thenReturn("Jaymee");
        when(userRepo.findById("u1")).thenReturn(Optional.of(user));

        ChatController.ChatRequest r = req("hello");
        r.userId = "u1";

        ArgumentCaptor<Object> captor = ArgumentCaptor.forClass(Object.class);

        Map<String, String> out = controller.chat(r);

        assertEquals("ok", out.get("reply"));

        verify(bodySpec).body(captor.capture());
        List<Map<String, Object>> messages = extractMessagesFromCapturedBody(captor.getValue());

        boolean hasNameSystem = messages.stream().anyMatch(m ->
                "system".equals(m.get("role")) &&
                        String.valueOf(m.get("content")).contains("The user's name is Jaymee")
        );

        assertTrue(hasNameSystem, "Expected username system message to be included");
    }

    // ✅ NEW TEST 2: covers guest history branch + safeRole(null) + filtering
    @Test
    void chat_whenGuestHistoryProvided_includesValidHistory_skipsInvalid() {
        stubChain();
        when(responseSpec.body(Map.class)).thenReturn(ollamaChatReply("ok"));

        ChatController.ChatRequest r = req("current msg");

        ChatController.HistoryMsg h1 = new ChatController.HistoryMsg();
        h1.role = "user";
        h1.content = "prev user";

        ChatController.HistoryMsg h2 = new ChatController.HistoryMsg();
        h2.role = null;                 // safeRole(null) path
        h2.content = "should skip";      // role becomes "" -> skipped

        ChatController.HistoryMsg h3 = new ChatController.HistoryMsg();
        h3.role = "hacker";             // invalid -> skipped
        h3.content = "skip";

        ChatController.HistoryMsg h4 = new ChatController.HistoryMsg();
        h4.role = "assistant";
        h4.content = "   ";             // blank -> skipped

        ChatController.HistoryMsg h5 = new ChatController.HistoryMsg();
        h5.role = "assistant";
        h5.content = "prev bot";

        r.history = Arrays.asList(h1, null, h2, h3, h4, h5);

        ArgumentCaptor<Object> captor = ArgumentCaptor.forClass(Object.class);

        Map<String, String> out = controller.chat(r);

        assertEquals("ok", out.get("reply"));

        verify(bodySpec).body(captor.capture());
        List<Map<String, Object>> messages = extractMessagesFromCapturedBody(captor.getValue());

        boolean hasPrevUser = messages.stream().anyMatch(m ->
                "user".equals(m.get("role")) && "prev user".equals(m.get("content"))
        );
        boolean hasPrevBot = messages.stream().anyMatch(m ->
                "assistant".equals(m.get("role")) && "prev bot".equals(m.get("content"))
        );

        assertTrue(hasPrevUser, "Expected valid user history to be included");
        assertTrue(hasPrevBot, "Expected valid assistant history to be included");
    }

    @Test
    void chat_whenUserIdProvided_butUserNotFound_stillWorks() {
        stubChain();
        when(responseSpec.body(Map.class)).thenReturn(ollamaChatReply("ok"));
        when(userRepo.findById("u404")).thenReturn(Optional.empty());

        ChatController.ChatRequest r = req("hello");
        r.userId = "u404";

        Map<String, String> out = controller.chat(r);

        assertEquals("ok", out.get("reply"));
        verify(userRepo).findById("u404");
    }

    @Test
    void chat_whenUserExists_butUsernameBlank_doesNotInjectNameRule() {
        stubChain();
        when(responseSpec.body(Map.class)).thenReturn(ollamaChatReply("ok"));

        // mock User entity
        com.example.chatbot.model.User user = mock(com.example.chatbot.model.User.class);
        when(user.getUsername()).thenReturn("   "); // blank username
        when(userRepo.findById("u1")).thenReturn(Optional.of(user));

        ChatController.ChatRequest r = req("hello");
        r.userId = "u1";

        ArgumentCaptor<Object> captor = ArgumentCaptor.forClass(Object.class);

        Map<String, String> out = controller.chat(r);

        assertEquals("ok", out.get("reply"));
        verify(bodySpec).body(captor.capture());

        @SuppressWarnings("unchecked")
        Map<String, Object> sent = (Map<String, Object>) captor.getValue();
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> messages = (List<Map<String, Object>>) sent.get("messages");

        boolean hasNameRule = messages.stream().anyMatch(m ->
                "system".equals(m.get("role")) &&
                        String.valueOf(m.get("content")).contains("The user's name is")
        );

        assertFalse(hasNameRule, "Name rule should NOT be added when username is blank");
    }

    @Test
    void chat_whenConversationIdNotFound_doesNotCrash() {
        when(convoRepo.findById("missing")).thenReturn(Optional.empty());

        stubChain();
        when(responseSpec.body(Map.class)).thenReturn(ollamaChatReply("ok"));

        ChatController.ChatRequest r = req("hello");
        r.conversationId = "missing";

        Map<String, String> out = controller.chat(r);

        assertEquals("ok", out.get("reply"));
        verify(convoRepo).findById("missing");
    }

    @Test
    void chat_whenConversationTurnsEmpty_skipsHistoryLoop() {
        Conversation convo = new Conversation();
        convo.setTurns(Collections.emptyList());
        when(convoRepo.findById("c1")).thenReturn(Optional.of(convo));

        stubChain();
        when(responseSpec.body(Map.class)).thenReturn(ollamaChatReply("ok"));

        ChatController.ChatRequest r = req("hello");
        r.conversationId = "c1";

        Map<String, String> out = controller.chat(r);

        assertEquals("ok", out.get("reply"));
    }

    @Test
    void chat_whenTurnsHaveMissingUserOrAssistant_messagesAreConditionallyAdded() {
        Conversation convo = new Conversation();
        List<Conversation.ChatTurn> turns = new ArrayList<>();

        Conversation.ChatTurn t1 = new Conversation.ChatTurn();
        t1.setUserMessage("User only");
        t1.setBotResponse("   "); // assistant empty -> should NOT add assistant
        turns.add(t1);

        Conversation.ChatTurn t2 = new Conversation.ChatTurn();
        t2.setUserMessage("   "); // user empty -> should NOT add user
        t2.setBotResponse("Bot only");
        turns.add(t2);

        convo.setTurns(turns);
        when(convoRepo.findById("c1")).thenReturn(Optional.of(convo));

        stubChain();
        when(responseSpec.body(Map.class)).thenReturn(ollamaChatReply("ok"));

        ChatController.ChatRequest r = req("hello");
        r.conversationId = "c1";

        ArgumentCaptor<Object> captor = ArgumentCaptor.forClass(Object.class);

        Map<String, String> out = controller.chat(r);

        assertEquals("ok", out.get("reply"));
        verify(bodySpec).body(captor.capture());

        @SuppressWarnings("unchecked")
        Map<String, Object> sent = (Map<String, Object>) captor.getValue();
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> messages = (List<Map<String, Object>>) sent.get("messages");

        boolean hasUserOnly = messages.stream().anyMatch(m ->
                "user".equals(m.get("role")) && "User only".equals(m.get("content")));
        boolean hasBotOnly = messages.stream().anyMatch(m ->
                "assistant".equals(m.get("role")) && "Bot only".equals(m.get("content")));

        assertTrue(hasUserOnly);
        assertTrue(hasBotOnly);
    }

    @Test
    void chat_whenOllamaResponseMissingMessage_returnsNoReplyFallback() {
        stubChain();
        when(responseSpec.body(Map.class)).thenReturn(Map.of("something", "else"));

        Map<String, String> out = controller.chat(req("hello"));

        assertEquals("(No reply from model)", out.get("reply"));
    }

    @Test
    void chat_whenOllamaResponseMessageNotAMap_returnsNoReplyFallback() {
        stubChain();
        when(responseSpec.body(Map.class)).thenReturn(Map.of("message", "not-a-map"));

        Map<String, String> out = controller.chat(req("hello"));

        assertEquals("(No reply from model)", out.get("reply"));
    }

    @Test
    void chat_whenOllamaResponseContentNull_returnsNoReplyFallback() {
        stubChain();

        Map<String, Object> msg = new HashMap<>();
        msg.put("content", null); // ✅ allowed in HashMap

        Map<String, Object> resp = new HashMap<>();
        resp.put("message", msg);

        when(responseSpec.body(Map.class)).thenReturn(resp);

        Map<String, String> out = controller.chat(req("hello"));

        assertEquals("(No reply from model)", out.get("reply"));
    }

    @Test
    void chat_whenHistoryRoleHasSpacesAndCaps_isNormalizedAndIncluded() {
        stubChain();
        when(responseSpec.body(Map.class)).thenReturn(ollamaChatReply("ok"));

        ChatController.ChatRequest r = req("hello");

        ChatController.HistoryMsg h = new ChatController.HistoryMsg();
        h.role = "  USER  ";
        h.content = "prev";

        r.history = List.of(h);

        ArgumentCaptor<Object> captor = ArgumentCaptor.forClass(Object.class);

        Map<String, String> out = controller.chat(r);
        assertEquals("ok", out.get("reply"));

        verify(bodySpec).body(captor.capture());
        @SuppressWarnings("unchecked")
        Map<String, Object> sent = (Map<String, Object>) captor.getValue();
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> messages = (List<Map<String, Object>>) sent.get("messages");

        boolean hasPrev = messages.stream().anyMatch(m ->
                "user".equals(m.get("role")) && "prev".equals(m.get("content")));
        assertTrue(hasPrev);
    }

    @Test
    void chat_whenOllamaReturnsNullResponse_returnsNoReplyFallback() {
        stubChain();
        when(responseSpec.body(Map.class)).thenReturn(null);

        Map<String, String> out = controller.chat(req("hello"));

        assertEquals("(No reply from model)", out.get("reply"));
    }

    @Test
    void chat_whenOllamaMessageHasNullContent_returnsNoReplyFallback() {
        stubChain();

        Map<String, Object> msg = new HashMap<>();
        msg.put("content", null);

        Map<String, Object> resp = new HashMap<>();
        resp.put("message", msg);

        when(responseSpec.body(Map.class)).thenReturn(resp);

        Map<String, String> out = controller.chat(req("hello"));

        assertEquals("(No reply from model)", out.get("reply"));
    }

    @Test
    void chat_whenUserIdBlank_skipsUserLookup_andStillReplies() {
        stubChain();
        when(responseSpec.body(Map.class)).thenReturn(ollamaChatReply("ok"));

        ChatController.ChatRequest r = req("hello");
        r.userId = "   ";          // blank
        r.conversationId = null;   // no convo
        r.history = null;          // no history

        Map<String, String> out = controller.chat(r);

        assertEquals("ok", out.get("reply"));
        verifyNoInteractions(userRepo); // ✅ proves branch skipped
    }

    @Test
    void chat_dbTurns_coverNullAndEmptyBranches_inLambda() {
        Conversation convo = new Conversation();
        List<Conversation.ChatTurn> turns = new ArrayList<>();

        // 1) userMessage == null, botResponse normal
        Conversation.ChatTurn t1 = new Conversation.ChatTurn();
        t1.setUserMessage(null);
        t1.setBotResponse("Bot reply");
        turns.add(t1);

        // 2) userMessage normal, botResponse == null
        Conversation.ChatTurn t2 = new Conversation.ChatTurn();
        t2.setUserMessage("User msg");
        t2.setBotResponse(null);
        turns.add(t2);

        // 3) both blank -> after trim becomes empty -> should NOT add
        Conversation.ChatTurn t3 = new Conversation.ChatTurn();
        t3.setUserMessage("   ");
        t3.setBotResponse("   ");
        turns.add(t3);

        convo.setTurns(turns);
        when(convoRepo.findById("c1")).thenReturn(Optional.of(convo));

        stubChain();
        when(responseSpec.body(Map.class)).thenReturn(ollamaChatReply("ok"));

        ChatController.ChatRequest r = req("current");
        r.conversationId = "c1";

        ArgumentCaptor<Object> captor = ArgumentCaptor.forClass(Object.class);

        Map<String, String> out = controller.chat(r);
        assertEquals("ok", out.get("reply"));

        // Capture the body and verify the messages list contains what we expect
        verify(bodySpec).body(captor.capture());

        @SuppressWarnings("unchecked")
        Map<String, Object> sent = (Map<String, Object>) captor.getValue();
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> messages = (List<Map<String, Object>>) sent.get("messages");

        // t1: only assistant should be added
        boolean hasBotReply = messages.stream().anyMatch(m ->
                "assistant".equals(m.get("role")) && "Bot reply".equals(m.get("content")));
        // t2: only user should be added
        boolean hasUserMsg = messages.stream().anyMatch(m ->
                "user".equals(m.get("role")) && "User msg".equals(m.get("content")));

        assertTrue(hasBotReply);
        assertTrue(hasUserMsg);

        // t3: blank should not be added
        boolean hasBlankUser = messages.stream().anyMatch(m ->
                "user".equals(m.get("role")) && "".equals(m.get("content")));
        boolean hasBlankBot = messages.stream().anyMatch(m ->
                "assistant".equals(m.get("role")) && "".equals(m.get("content")));

        assertFalse(hasBlankUser);
        assertFalse(hasBlankBot);
    }

    @Test
    void chat_whenUserFound_butUsernameNull_doesNotInjectNameRule() {
        stubChain();
        when(responseSpec.body(Map.class)).thenReturn(ollamaChatReply("ok"));

        com.example.chatbot.model.User user = mock(com.example.chatbot.model.User.class);
        when(user.getUsername()).thenReturn(null); // ✅ missing branch
        when(userRepo.findById("u1")).thenReturn(Optional.of(user));

        ChatController.ChatRequest r = req("hello");
        r.userId = "u1";

        Map<String, String> out = controller.chat(r);

        assertEquals("ok", out.get("reply"));
    }

    @Test
    void chat_whenOllamaResponseMessageMissingOrWrongType_returnsNoReplyFallback() {
        stubChain();

        // message missing
        when(responseSpec.body(Map.class)).thenReturn(Map.of("x", "y"));

        ChatController.ChatRequest r = req("hello");
        r.conversationId = null;
        r.history = null;
        r.userId = null;

        Map<String, String> out = controller.chat(r);

        assertEquals("(No reply from model)", out.get("reply"));
    }

    @Test
    void chat_whenOllamaMessageMapButContentMissing_returnsNoReplyFallback() {
        stubChain();

        Map<String, Object> msg = new HashMap<>();
        // no "content" key at all
        Map<String, Object> resp = new HashMap<>();
        resp.put("message", msg);

        when(responseSpec.body(Map.class)).thenReturn(resp);

        Map<String, String> out = controller.chat(req("hello"));

        assertEquals("(No reply from model)", out.get("reply"));
    }

    @Test
    void chat_whenConversationTurnsNull_skipsSafely() {
        Conversation convo = mock(Conversation.class);
        when(convo.getTurns()).thenReturn(null); // ✅ covers turns == null branch
        when(convoRepo.findById("c1")).thenReturn(Optional.of(convo));

        stubChain();
        when(responseSpec.body(Map.class)).thenReturn(ollamaChatReply("ok"));

        ChatController.ChatRequest r = req("hello");
        r.conversationId = "c1";

        Map<String, String> out = controller.chat(r);

        assertEquals("ok", out.get("reply"));
    }

    @Test
    void chat_history_coversNullContent_emptyContent_systemRole_andInvalidRole() {
        stubChain();
        when(responseSpec.body(Map.class)).thenReturn(ollamaChatReply("ok"));

        ChatController.ChatRequest r = req("hello");
        r.conversationId = "   "; // blank -> hasConvoId should be false

        ChatController.HistoryMsg h1 = new ChatController.HistoryMsg();
        h1.role = "system";
        h1.content = "system note"; // ✅ allowed role branch

        ChatController.HistoryMsg h2 = new ChatController.HistoryMsg();
        h2.role = "user";
        h2.content = null; // ✅ content == null -> becomes "" -> triggers content.isEmpty() continue

        ChatController.HistoryMsg h3 = new ChatController.HistoryMsg();
        h3.role = "unknown";  // ✅ invalid role -> OR condition false
        h3.content = "skip me";

        r.history = Arrays.asList(h1, h2, h3);

        ArgumentCaptor<Object> captor = ArgumentCaptor.forClass(Object.class);

        Map<String, String> out = controller.chat(r);

        assertEquals("ok", out.get("reply"));

        verify(bodySpec).body(captor.capture());

        @SuppressWarnings("unchecked")
        Map<String, Object> sent = (Map<String, Object>) captor.getValue();
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> messages = (List<Map<String, Object>>) sent.get("messages");

        boolean hasSystem = messages.stream().anyMatch(m ->
                "system".equals(m.get("role")) && "system note".equals(m.get("content")));
        assertTrue(hasSystem, "Expected system history message included");

        boolean hasInvalid = messages.stream().anyMatch(m ->
                "unknown".equals(m.get("role")) && "skip me".equals(m.get("content")));
        assertFalse(hasInvalid, "Invalid role must not be included");
    }

    @Test
    void chat_whenHistoryIsEmptyList_hasHistoryIsFalse() {
        stubChain();
        when(responseSpec.body(Map.class)).thenReturn(ollamaChatReply("ok"));

        ChatController.ChatRequest r = req("hello");
        r.history = Collections.emptyList(); // ✅ history != null but empty

        Map<String, String> out = controller.chat(r);

        assertEquals("ok", out.get("reply"));
    }
}