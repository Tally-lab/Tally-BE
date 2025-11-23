package com.tally.domain;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.Objects;

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

    private Owner owner;

    @JsonProperty("default_branch")
    private String defaultBranch;

    @JsonProperty("created_at")
    private String createdAt;

    @JsonProperty("updated_at")
    private String updatedAt;

    // ✅ Fork 정보 추가
    private Boolean fork;

    @JsonProperty("parent")
    private ParentRepository parent;

    // Owner 내부 클래스
    @Data
    public static class Owner {
        private String login;
        private Long id;

        @JsonProperty("avatar_url")
        private String avatarUrl;

        private String type;  // "User" 또는 "Organization"
    }

    // ✅ Parent Repository 내부 클래스 추가
    @Data
    public static class ParentRepository {
        private String name;

        @JsonProperty("full_name")
        private String fullName;

        private Owner owner;

        @JsonProperty("html_url")
        private String url;
    }

    // 프론트엔드 호환성을 위한 헬퍼 메서드
    public String getOwnerLogin() {
        return owner != null ? owner.getLogin() : null;
    }

    // 중복 제거를 위한 equals/hashCode (id 기준)
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        GitHubRepository that = (GitHubRepository) o;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}