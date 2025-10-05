package com.tally.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class User {
    private String id;
    private String githubId;              // GitHub 사용자 ID
    private String githubLogin;           // GitHub username
    private String githubAccessToken;     // OAuth Access Token
    private String avatarUrl;             // 프로필 이미지
    private LocalDateTime createdAt;
    private LocalDateTime lastLoginAt;
}