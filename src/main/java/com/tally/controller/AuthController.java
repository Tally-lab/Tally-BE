package com.tally.controller;

import com.tally.domain.User;
import com.tally.service.AuthService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {
    private final AuthService authService;

    /**
     * 로그인 (Access Token 직접 입력)
     */
    @PostMapping("/login")
    public ResponseEntity<User> login(@RequestBody Map<String, String> request) {
        String accessToken = request.get("accessToken");
        User user = authService.login(accessToken);
        return ResponseEntity.ok(user);
    }

    /**
     * GitHub OAuth 로그인 페이지로 리다이렉트
     */
    @GetMapping("/github")
    public ResponseEntity<?> redirectToGitHub() {
        String authUrl = authService.getAuthorizationUrl();
        return ResponseEntity.ok(Map.of("authUrl", authUrl));
    }

    /**
     * GitHub OAuth Callback
     */
    @GetMapping("/callback")
    public ResponseEntity<?> handleCallback(@RequestParam String code) {
        try {
            User user = authService.exchangeCodeForToken(code);

            return ResponseEntity.ok(Map.of(
                    "message", "Authentication successful",
                    "accessToken", user.getAccessToken(),
                    "user", user
            ));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of(
                    "error", "Authentication failed",
                    "message", e.getMessage()
            ));
        }
    }
}