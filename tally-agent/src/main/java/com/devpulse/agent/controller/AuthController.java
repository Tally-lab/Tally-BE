package com.devpulse.agent.controller;

import com.devpulse.agent.service.GitHubOAuthService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final GitHubOAuthService gitHubOAuthService;

    @GetMapping("/github")
    public Map<String, String> getAuthUrl() {
        return Map.of("authUrl", gitHubOAuthService.getAuthorizationUrl());
    }

    @PostMapping("/github/callback")
    public ResponseEntity<?> handleCallback(@RequestBody Map<String, String> request) {
        String code = request.get("code");
        if (code == null || code.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Authorization code is required"));
        }
        try {
            return ResponseEntity.ok(gitHubOAuthService.exchangeCodeForUser(code));
        } catch (Exception e) {
            log.error("OAuth callback failed", e);
            return ResponseEntity.internalServerError().body(Map.of("error", e.getMessage()));
        }
    }
}
