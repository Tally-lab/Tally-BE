package com.tally.controller;

import com.tally.domain.User;
import com.tally.service.AuthService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.view.RedirectView;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @Value("${github.client-id}")
    private String clientId;

    @Value("${github.redirect-uri}")
    private String redirectUri;

    @Value("${frontend.url:http://localhost:5173}")
    private String frontendUrl;

    @GetMapping("/github")
    public ResponseEntity<Map<String, String>> getGitHubAuthUrl() {
        // ✅ prompt=consent 추가: 매번 권한 승인 페이지 표시
        String authUrl = String.format(
                "https://github.com/login/oauth/authorize?client_id=%s&redirect_uri=%s&scope=user:email,repo,read:org&prompt=consent",
                clientId,
                redirectUri
        );

        Map<String, String> response = new HashMap<>();
        response.put("authUrl", authUrl);

        log.info("Generated GitHub auth URL with prompt=consent");
        return ResponseEntity.ok(response);
    }

    @GetMapping("/callback")
    public RedirectView handleGitHubCallback(@RequestParam String code) {
        try {
            log.info("Received GitHub callback with code: {}", code.substring(0, 5) + "...");

            String accessToken = authService.getAccessToken(code);
            User user = authService.getUserInfo(accessToken);

            user.setAccessToken(accessToken);
            authService.saveUser(user);

            // 프론트엔드로 리다이렉트 (토큰과 사용자 정보 전달)
            String redirectUrl = String.format(
                    "%s/auth/callback?accessToken=%s&userId=%s&username=%s",
                    frontendUrl,
                    URLEncoder.encode(accessToken, StandardCharsets.UTF_8.toString()),
                    URLEncoder.encode(user.getId(), StandardCharsets.UTF_8.toString()),
                    URLEncoder.encode(user.getUsername(), StandardCharsets.UTF_8.toString())
            );

            log.info("Redirecting to frontend: {}", frontendUrl + "/auth/callback");

            RedirectView redirectView = new RedirectView();
            redirectView.setUrl(redirectUrl);
            redirectView.setStatusCode(HttpStatus.FOUND);
            return redirectView;

        } catch (Exception e) {
            log.error("Authentication failed", e);

            RedirectView redirectView = new RedirectView();
            redirectView.setUrl(frontendUrl + "/?error=auth_failed");
            return redirectView;
        }
    }

    @PostMapping("/login")
    public ResponseEntity<User> login(@RequestBody Map<String, String> request) {
        String accessToken = request.get("accessToken");

        try {
            User user = authService.getUserInfo(accessToken);
            user.setAccessToken(accessToken);
            authService.saveUser(user);

            log.info("User logged in: {}", user.getUsername());
            return ResponseEntity.ok(user);

        } catch (Exception e) {
            log.error("Login failed", e);
            return ResponseEntity.badRequest().build();
        }
    }
}