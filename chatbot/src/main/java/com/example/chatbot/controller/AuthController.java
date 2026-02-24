package com.example.chatbot.controller;

import com.example.chatbot.model.User;
import com.example.chatbot.repository.UserRepository;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/auth")
@CrossOrigin(origins = "http://localhost:4200")
public class AuthController {

    private final UserRepository userRepository;

    public AuthController(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    // ✅ LIVE CHECK USERNAME
    @GetMapping("/check-username")
    public ResponseEntity<?> checkUsername(@RequestParam("username") String username) {
        String u = username == null ? "" : username.trim();
        boolean exists = !u.isBlank() && userRepository.existsByUsername(u);
        return ResponseEntity.ok(Map.of("exists", exists));
    }

    // ✅ LIVE CHECK EMAIL
    @GetMapping("/check-email")
    public ResponseEntity<?> checkEmail(@RequestParam("email") String email) {
        String e = email == null ? "" : email.trim().toLowerCase();
        boolean exists = !e.isBlank() && userRepository.existsByEmail(e);
        return ResponseEntity.ok(Map.of("exists", exists));
    }

    // ✅ SIGNUP
    @PostMapping("/signup")
    public ResponseEntity<?> signup(@RequestBody User user) {

        String username = user.getUsername() == null ? "" : user.getUsername().trim();
        String email = user.getEmail() == null ? "" : user.getEmail().trim().toLowerCase();
        String password = user.getPassword() == null ? "" : user.getPassword();

        Map<String, String> errors = new HashMap<>();

        // ✅ Required fields
        if (username.isBlank()) {
            errors.put("username", "Username is required.");
        }

        if (email.isBlank()) {
            errors.put("email", "Email is required.");
        }

        if (password.isBlank()) {
            errors.put("password", "Password is required.");
        }

        // ✅ Password strength
        if (!password.isBlank() && !isStrongPassword(password)) {
            errors.put("password",
                    "Password must be at least 6 characters and include uppercase, lowercase, number, and special character.");
        }

        // ✅ Duplicate checks
        if (!username.isBlank() && userRepository.existsByUsername(username)) {
            errors.put("username", "Username already exists.");
        }

        if (!email.isBlank() && userRepository.existsByEmail(email)) {
            errors.put("email", "Email already exists.");
        }

        // 🚨 If any error exists → return ALL errors at once
        if (!errors.isEmpty()) {
            return ResponseEntity.status(409).body(errors);
        }

        // Normalize values
        user.setUsername(username);
        user.setEmail(email);

        try {
            User saved = userRepository.save(user);
            saved.setPassword(null); // don't expose password
            return ResponseEntity.ok(saved);

        } catch (DuplicateKeyException e) {
            return ResponseEntity.status(409)
                    .body(Map.of("general", "Email or Username already exists."));
        }
    }

    // ✅ LOGIN
    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody Map<String, String> body) {

        String email = body.get("email") == null ? "" : body.get("email").trim().toLowerCase();
        String password = body.get("password") == null ? "" : body.get("password");

        var userOpt = userRepository.findByEmail(email);

        if (userOpt.isEmpty()) {
            return ResponseEntity.status(401)
                    .body(Map.of("message", "Invalid email or password"));
        }

        var user = userOpt.get();

        if (!user.getPassword().equals(password)) {
            return ResponseEntity.status(401)
                    .body(Map.of("message", "Invalid email or password"));
        }

        user.setPassword(null);
        return ResponseEntity.ok(user);
    }

    // ✅ PASSWORD VALIDATION
    private boolean isStrongPassword(String p) {
        if (p == null) return false;

        boolean minLen = p.length() >= 6;
        boolean upper = p.matches(".*[A-Z].*");
        boolean lower = p.matches(".*[a-z].*");
        boolean num = p.matches(".*\\d.*");
        boolean special = p.matches(".*[^A-Za-z0-9].*");

        return minLen && upper && lower && num && special;
    }
}