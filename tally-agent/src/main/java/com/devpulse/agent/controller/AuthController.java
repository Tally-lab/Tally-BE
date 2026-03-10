package com.devpulse.agent.controller;

import com.devpulse.agent.service.GitHubOAuthService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

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
    public Map<String, Object> handleCallback(@RequestBody Map<String, String> request) {
        String code = request.get("code");
        if (code == null || code.isBlank()) {
            throw new IllegalArgumentException("Authorization code is required");
        }
        return gitHubOAuthService.exchangeCodeForUser(code);
    }
}
