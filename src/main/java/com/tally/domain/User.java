package com.tally.domain;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class User {
    private String id;

    @JsonProperty("login")  // 이 줄이 있어야 함!
    private String username;

    private String accessToken;
    private String email;

    @JsonProperty("avatar_url")
    private String avatarUrl;
}