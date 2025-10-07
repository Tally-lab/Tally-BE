package com.tally.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class GitHubUserResponse {
    private String login;
    private String email;

    @JsonProperty("avatar_url")
    private String avatarUrl;

    private String name;
    private String bio;

    @JsonProperty("html_url")
    private String htmlUrl;
}