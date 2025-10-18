package com.tally.domain;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class User {
    private String id;

    @JsonProperty("login")
    private String username;

    private String accessToken;
    private String email;

    @JsonProperty("avatar_url")
    private String avatarUrl;

    /**
     * ✅ GitHub API 호환을 위한 getLogin() 메서드
     * @JsonIgnore로 Jackson 직렬화에서 제외
     */
    @JsonIgnore
    public String getLogin() {
        return this.username;
    }

    /**
     * ✅ GitHub API 호환을 위한 setLogin() 메서드
     */
    public void setLogin(String login) {
        this.username = login;
    }
}