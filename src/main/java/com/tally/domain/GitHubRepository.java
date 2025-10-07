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
public class GitHubRepository {
    private String id;
    private String name;                  // 레포지토리 이름
    private String fullName;              // owner/repo
    private String description;
    private String url;
    private String owner;
    private boolean isPrivate;
    private String defaultBranch;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}