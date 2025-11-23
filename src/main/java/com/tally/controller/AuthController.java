package com.tally.controller;

import com.tally.domain.User;
import com.tally.service.AuthService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
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

    /**
     * GitHub OAuth URL 생성
     */
    @GetMapping("/github")
    public ResponseEntity<Map<String, String>> getGitHubAuthUrl() {
        String authUrl = String.format(
                "https://github.com/login/oauth/authorize?client_id=%s&redirect_uri=%s&scope=user:email,repo,read:org",
                clientId,
                redirectUri
        );

        Map<String, String> response = new HashMap<>();
        response.put("authUrl", authUrl);

        log.info("Generated GitHub auth URL");
        return ResponseEntity.ok(response);
    }

    /**
     * ✅ GitHub OAuth 콜백 처리 (브라우저 리다이렉트용)
     * 브라우저가 직접 이 URL로 리다이렉트되므로 RedirectView 사용
     */
    @GetMapping("/callback")
    public RedirectView handleGitHubCallback(@RequestParam String code) {
        try {
            log.info("Received GitHub callback with code: {}", code.substring(0, 5) + "...");

            String accessToken = authService.getAccessToken(code);
            User user = authService.getUserInfo(accessToken);

            user.setAccessToken(accessToken);
            authService.saveUser(user);

            // ✅ 프론트엔드로 리다이렉트 (토큰과 사용자 정보 전달)
            String redirectUrl = String.format(
                    "%s/auth/callback?accessToken=%s&userId=%s&username=%s&avatarUrl=%s",
                    frontendUrl,
                    URLEncoder.encode(accessToken, StandardCharsets.UTF_8),
                    URLEncoder.encode(user.getId(), StandardCharsets.UTF_8),
                    URLEncoder.encode(user.getUsername(), StandardCharsets.UTF_8),
                    URLEncoder.encode(user.getAvatarUrl() != null ? user.getAvatarUrl() : "", StandardCharsets.UTF_8)
            );

            log.info("Redirecting to frontend: {}", frontendUrl + "/auth/callback");
            return new RedirectView(redirectUrl);

        } catch (Exception e) {
            log.error("Authentication failed", e);
            return new RedirectView(frontendUrl + "/?error=auth_failed");
        }
    }

    /**
     * ✅ 토큰 기반 로그인 (개발용 - 토큰 직접 입력)
     */
    @PostMapping("/login")
    public ResponseEntity<User> login(@RequestBody Map<String, String> request) {
        String accessToken = request.get("accessToken");

        try {
            User user = authService.getUserInfo(accessToken);
            user.setAccessToken(accessToken);
            authService.saveUser(user);

            log.info("User logged in via token: {}", user.getUsername());
            return ResponseEntity.ok(user);

        } catch (Exception e) {
            log.error("Login failed", e);
            return ResponseEntity.status(401).build();
        }
    }
}