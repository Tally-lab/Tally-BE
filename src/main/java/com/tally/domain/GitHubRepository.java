package com.tally.domain;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
public class GitHubRepository {
    private Long id;

    private String name;

    @JsonProperty("full_name")
    private String fullName;

    private String description;

    @JsonProperty("private")
    private Boolean isPrivate;

    @JsonProperty("html_url")
    private String url;

    private Owner owner;  // String에서 Owner 객체로 변경!

    @JsonProperty("default_branch")
    private String defaultBranch;

    @JsonProperty("created_at")
    private String createdAt;

    @JsonProperty("updated_at")
    private String updatedAt;

    // Owner 내부 클래스 추가
    @Data
    public static class Owner {
        private String login;  // 실제 username
        private Long id;

        @JsonProperty("avatar_url")
        private String avatarUrl;
    }

    // 프론트엔드 호환성을 위한 헬퍼 메서드
    public String getOwnerLogin() {
        return owner != null ? owner.getLogin() : null;
    }
}