package com.tally.controller;

import com.tally.domain.User;
import com.tally.service.AuthService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.view.RedirectView;

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
     * GitHub OAuth Callback - 프론트엔드로 리다이렉트
     */
    @GetMapping("/callback")
    public RedirectView handleCallback(@RequestParam String code) {
        try {
            User user = authService.exchangeCodeForToken(code);

            // ✅ 프론트엔드로 리다이렉트 (accessToken과 user 정보를 URL 파라미터로 전달)
            String redirectUrl = String.format(
                    "http://localhost:5173/auth/callback?accessToken=%s&userId=%s&username=%s&avatarUrl=%s",
                    user.getAccessToken(),
                    user.getId(),
                    user.getUsername(),
                    user.getAvatarUrl() != null ? user.getAvatarUrl() : ""
            );

            return new RedirectView(redirectUrl);

        } catch (Exception e) {
            // 에러 시에도 프론트엔드로 리다이렉트
            return new RedirectView("http://localhost:5173/?error=" + e.getMessage());
        }
    }
}