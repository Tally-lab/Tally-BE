package com.tally.service;

import com.tally.domain.User;
import com.tally.repository.UserRepositoryImpl;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepositoryImpl userRepository;

    public User loginWithGitHub(String githubLogin, String githubId, String accessToken, String avatarUrl) {
        return userRepository.findByGithubLogin(githubLogin)
                .map(user -> {
                    // 기존 사용자 - 토큰 및 로그인 시간 업데이트
                    User updatedUser = User.builder()
                            .id(user.getId())
                            .githubId(githubId)
                            .githubLogin(githubLogin)
                            .githubAccessToken(accessToken)
                            .avatarUrl(avatarUrl)
                            .createdAt(user.getCreatedAt())
                            .lastLoginAt(LocalDateTime.now())
                            .build();
                    userRepository.save(updatedUser);
                    log.info("Existing GitHub user logged in: {}", githubLogin);
                    return updatedUser;
                })
                .orElseGet(() -> {
                    // 신규 사용자 - 자동 계정 생성
                    User newUser = User.builder()
                            .id(UUID.randomUUID().toString())
                            .githubId(githubId)
                            .githubLogin(githubLogin)
                            .githubAccessToken(accessToken)
                            .avatarUrl(avatarUrl)
                            .createdAt(LocalDateTime.now())
                            .lastLoginAt(LocalDateTime.now())
                            .build();
                    userRepository.save(newUser);
                    log.info("New GitHub user created: {}", githubLogin);
                    return newUser;
                });
    }

    public User getUserByGithubLogin(String githubLogin) {
        return userRepository.findByGithubLogin(githubLogin)
                .orElseThrow(() -> new RuntimeException("User not found: " + githubLogin));
    }
}