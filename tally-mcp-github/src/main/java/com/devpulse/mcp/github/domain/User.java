package com.devpulse.mcp.github.domain;

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

    @JsonIgnore
    public String getLogin() {
        return this.username;
    }

    public void setLogin(String login) {
        this.username = login;
    }
}
