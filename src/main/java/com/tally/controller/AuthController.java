package com.tally.controller;

import com.tally.domain.User;
import com.tally.service.AuthService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/github")
    public ResponseEntity<User> loginWithGitHub(@RequestBody Map<String, String> request) {
        String githubLogin = request.get("githubLogin");
        String githubId = request.get("githubId");
        String accessToken = request.get("accessToken");
        String avatarUrl = request.get("avatarUrl");

        log.info("GitHub login request for user: {}", githubLogin);

        User user = authService.loginWithGitHub(githubLogin, githubId, accessToken, avatarUrl);
        return ResponseEntity.ok(user);
    }

    @GetMapping("/user/{githubLogin}")
    public ResponseEntity<User> getUser(@PathVariable String githubLogin) {
        User user = authService.getUserByGithubLogin(githubLogin);
        return ResponseEntity.ok(user);
    }
}