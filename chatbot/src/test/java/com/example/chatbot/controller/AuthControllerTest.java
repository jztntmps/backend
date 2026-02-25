package com.example.chatbot.controller;

import com.example.chatbot.model.User;
import com.example.chatbot.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthControllerTest {

    @Mock private UserRepository userRepository;

    private AuthController controller;

    @BeforeEach
    void setUp() {
        controller = new AuthController(userRepository);
    }

    // -------------------------
    // check-username
    // -------------------------

    @Test
    void checkUsername_null_returnsExistsFalse_andDoesNotHitRepo() {
        ResponseEntity<?> res = controller.checkUsername(null);

        assertEquals(HttpStatus.OK, res.getStatusCode());
        assertEquals(Map.of("exists", false), res.getBody());
        verifyNoInteractions(userRepository);
    }

    @Test
    void checkUsername_blank_returnsExistsFalse_andDoesNotHitRepo() {
        ResponseEntity<?> res = controller.checkUsername("   ");

        assertEquals(HttpStatus.OK, res.getStatusCode());
        assertEquals(Map.of("exists", false), res.getBody());
        verifyNoInteractions(userRepository);
    }

    @Test
    void checkUsername_nonBlank_checksRepo() {
        when(userRepository.existsByUsername("john")).thenReturn(true);

        ResponseEntity<?> res = controller.checkUsername("  john  ");

        assertEquals(HttpStatus.OK, res.getStatusCode());
        assertEquals(Map.of("exists", true), res.getBody());
        verify(userRepository).existsByUsername("john");
    }

    // -------------------------
    // check-email
    // -------------------------

    @Test
    void checkEmail_null_returnsExistsFalse_andDoesNotHitRepo() {
        ResponseEntity<?> res = controller.checkEmail(null);

        assertEquals(HttpStatus.OK, res.getStatusCode());
        assertEquals(Map.of("exists", false), res.getBody());
        verifyNoInteractions(userRepository);
    }

    @Test
    void checkEmail_blank_returnsExistsFalse_andDoesNotHitRepo() {
        ResponseEntity<?> res = controller.checkEmail("   ");

        assertEquals(HttpStatus.OK, res.getStatusCode());
        assertEquals(Map.of("exists", false), res.getBody());
        verifyNoInteractions(userRepository);
    }

    @Test
    void checkEmail_nonBlank_lowercasesAndChecksRepo() {
        when(userRepository.existsByEmail("test@example.com")).thenReturn(true);

        ResponseEntity<?> res = controller.checkEmail("  TEST@Example.Com  ");

        assertEquals(HttpStatus.OK, res.getStatusCode());
        assertEquals(Map.of("exists", true), res.getBody());
        verify(userRepository).existsByEmail("test@example.com");
    }

    // -------------------------
    // signup
    // -------------------------

    private static User user(String username, String email, String password) {
        User u = new User();
        u.setUsername(username);
        u.setEmail(email);
        u.setPassword(password);
        return u;
    }

    @Test
    void signup_whenAllFieldsBlank_returnsAllRequiredErrors_409() {
        ResponseEntity<?> res = controller.signup(user("   ", "   ", "   "));

        assertEquals(409, res.getStatusCode().value());

        assertNotNull(res.getBody());
        assertTrue(res.getBody() instanceof Map);

        @SuppressWarnings("unchecked")
        Map<String, String> errors = (Map<String, String>) res.getBody();

        assertEquals("Username is required.", errors.get("username"));
        assertEquals("Email is required.", errors.get("email"));
        assertEquals("Password is required.", errors.get("password"));

        verifyNoInteractions(userRepository);
    }

    @Test
    void signup_whenPasswordWeak_returnsPasswordStrengthError_409() {
        // controller will still run duplicate checks if username/email are not blank
        when(userRepository.existsByUsername("john")).thenReturn(false);
        when(userRepository.existsByEmail("john@example.com")).thenReturn(false);

        ResponseEntity<?> res = controller.signup(user("john", "john@example.com", "abc"));

        assertEquals(409, res.getStatusCode().value());

        assertNotNull(res.getBody());
        assertTrue(res.getBody() instanceof Map);

        @SuppressWarnings("unchecked")
        Map<String, String> errors = (Map<String, String>) res.getBody();

        assertTrue(errors.get("password").contains("at least 6 characters"));

        // it should NOT save when errors exist
        verify(userRepository).existsByUsername("john");
        verify(userRepository).existsByEmail("john@example.com");
        verify(userRepository, never()).save(any());
    }

    @Test
    void signup_whenUsernameDuplicate_returnsUsernameExists_409() {
        when(userRepository.existsByUsername("john")).thenReturn(true);
        when(userRepository.existsByEmail("john@example.com")).thenReturn(false);

        ResponseEntity<?> res = controller.signup(user(" john ", "JOHN@EXAMPLE.COM", "Aa1!aa"));

        assertEquals(409, res.getStatusCode().value());

        @SuppressWarnings("unchecked")
        Map<String, String> errors = (Map<String, String>) res.getBody();

        assertEquals("Username already exists.", errors.get("username"));
        assertNull(errors.get("email"));

        verify(userRepository).existsByUsername("john");
        verify(userRepository).existsByEmail("john@example.com");
        verify(userRepository, never()).save(any());
    }

    @Test
    void signup_whenEmailDuplicate_returnsEmailExists_409() {
        when(userRepository.existsByUsername("john")).thenReturn(false);
        when(userRepository.existsByEmail("john@example.com")).thenReturn(true);

        ResponseEntity<?> res = controller.signup(user("john", " john@EXAMPLE.com ", "Aa1!aa"));

        assertEquals(409, res.getStatusCode().value());

        @SuppressWarnings("unchecked")
        Map<String, String> errors = (Map<String, String>) res.getBody();

        assertEquals("Email already exists.", errors.get("email"));

        verify(userRepository).existsByUsername("john");
        verify(userRepository).existsByEmail("john@example.com");
        verify(userRepository, never()).save(any());
    }

    @Test
    void signup_success_normalizesAndReturnsSavedUserWithoutPassword() {
        when(userRepository.existsByUsername("john")).thenReturn(false);
        when(userRepository.existsByEmail("john@example.com")).thenReturn(false);

        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        User input = user("  john  ", "  JOHN@Example.Com  ", "Aa1!aa");

        ResponseEntity<?> res = controller.signup(input);

        assertEquals(HttpStatus.OK, res.getStatusCode());
        assertInstanceOf(User.class, res.getBody());

        User saved = (User) res.getBody();
        assertEquals("john", saved.getUsername());
        assertEquals("john@example.com", saved.getEmail());
        assertNull(saved.getPassword());

        verify(userRepository).save(any(User.class));
    }

    @Test
    void signup_whenRepoThrowsDuplicateKeyException_returnsGeneralError_409() {
        when(userRepository.existsByUsername("john")).thenReturn(false);
        when(userRepository.existsByEmail("john@example.com")).thenReturn(false);

        when(userRepository.save(any(User.class))).thenThrow(new DuplicateKeyException("dup"));

        ResponseEntity<?> res = controller.signup(user("john", "john@example.com", "Aa1!aa"));

        assertEquals(409, res.getStatusCode().value());
        assertEquals(Map.of("general", "Email or Username already exists."), res.getBody());
    }

    // -------------------------
    // login
    // -------------------------

    @Test
    void login_whenUserNotFound_returns401() {
        when(userRepository.findByEmail("a@b.com")).thenReturn(Optional.empty());

        Map<String, String> body = new HashMap<>();
        body.put("email", "  A@B.COM  ");
        body.put("password", "x");

        ResponseEntity<?> res = controller.login(body);

        assertEquals(401, res.getStatusCode().value());
        assertEquals(Map.of("message", "Invalid email or password"), res.getBody());
    }

    @Test
    void login_whenPasswordWrong_returns401() {
        User u = new User();
        u.setEmail("a@b.com");
        u.setPassword("correct");

        when(userRepository.findByEmail("a@b.com")).thenReturn(Optional.of(u));

        Map<String, String> body = new HashMap<>();
        body.put("email", "a@b.com");
        body.put("password", "wrong");

        ResponseEntity<?> res = controller.login(body);

        assertEquals(401, res.getStatusCode().value());
        assertEquals(Map.of("message", "Invalid email or password"), res.getBody());
    }

    @Test
    void login_success_returnsUserWithoutPassword() {
        User u = new User();
        u.setEmail("a@b.com");
        u.setPassword("pw");

        when(userRepository.findByEmail("a@b.com")).thenReturn(Optional.of(u));

        Map<String, String> body = new HashMap<>();
        body.put("email", "  A@B.COM  ");
        body.put("password", "pw");

        ResponseEntity<?> res = controller.login(body);

        assertEquals(HttpStatus.OK, res.getStatusCode());
        assertInstanceOf(User.class, res.getBody());

        User out = (User) res.getBody();
        assertEquals("a@b.com", out.getEmail());
        assertNull(out.getPassword());
    }

    @Test
    void login_whenMissingFields_treatedAsBlank_andFails401() {
        when(userRepository.findByEmail("")).thenReturn(Optional.empty());

        Map<String, String> body = new HashMap<>(); // no keys

        ResponseEntity<?> res = controller.login(body);

        assertEquals(401, res.getStatusCode().value());
        assertEquals(Map.of("message", "Invalid email or password"), res.getBody());
    }

    @Test
    void checkUsername_nonBlank_repoReturnsFalse() {
        when(userRepository.existsByUsername("john")).thenReturn(false);

        ResponseEntity<?> res = controller.checkUsername("john");

        assertEquals(Map.of("exists", false), res.getBody());
    }

    @Test
    void checkEmail_nonBlank_repoReturnsFalse() {
        when(userRepository.existsByEmail("a@b.com")).thenReturn(false);

        ResponseEntity<?> res = controller.checkEmail("a@b.com");

        assertEquals(Map.of("exists", false), res.getBody());
    }

    @Test
    void signup_passwordNull_returnsRequiredError() {
        ResponseEntity<?> res = controller.signup(user("john", "john@example.com", null));

        assertEquals(409, res.getStatusCode().value());
    }

    @Test
    void signup_passwordTooShort() {
        ResponseEntity<?> res = controller.signup(user("john", "john@example.com", "Aa1!"));

        assertEquals(409, res.getStatusCode().value());
    }

    @Test
    void signup_passwordMissingUppercase() {
        ResponseEntity<?> res = controller.signup(user("john", "john@example.com", "aa1!aa"));

        assertEquals(409, res.getStatusCode().value());
    }

    @Test
    void signup_passwordMissingLowercase() {
        ResponseEntity<?> res = controller.signup(user("john", "john@example.com", "AA1!AA"));

        assertEquals(409, res.getStatusCode().value());
    }

    @Test
    void signup_passwordMissingNumber() {
        ResponseEntity<?> res = controller.signup(user("john", "john@example.com", "Aa!AaA"));

        assertEquals(409, res.getStatusCode().value());
    }

    @Test
    void signup_passwordMissingSpecialChar() {
        ResponseEntity<?> res = controller.signup(user("john", "john@example.com", "Aa1AaA"));

        assertEquals(409, res.getStatusCode().value());
    }

    @Test
    void signup_usernameBlank_emailDuplicate() {
        when(userRepository.existsByEmail("john@example.com")).thenReturn(true);

        ResponseEntity<?> res = controller.signup(
                user("   ", "john@example.com", "Aa1!aa")
        );

        assertEquals(409, res.getStatusCode().value());
    }

    @Test
    void signup_whenUsernameAndEmailNull_coversNullNormalizationBranches() {
        // user.getUsername() == null and user.getEmail() == null
        ResponseEntity<?> res = controller.signup(user(null, null, "Aa1!aa"));

        assertEquals(409, res.getStatusCode().value());

        @SuppressWarnings("unchecked")
        Map<String, String> errors = (Map<String, String>) res.getBody();

        assertEquals("Username is required.", errors.get("username"));
        assertEquals("Email is required.", errors.get("email"));

        // password is present and strong, so no password error expected
        assertNull(errors.get("password"));
    }

    @Test
    void isStrongPassword_whenNull_returnsFalse_viaReflection() throws Exception {
        var m = AuthController.class.getDeclaredMethod("isStrongPassword", String.class);
        m.setAccessible(true);

        boolean result = (boolean) m.invoke(controller, new Object[]{null});

        assertFalse(result);
    }
}