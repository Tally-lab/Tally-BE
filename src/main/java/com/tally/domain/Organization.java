package com.tally.domain;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
public class Organization {
    private Long id;

    private String login;

    @JsonProperty("avatar_url")
    private String avatarUrl;

    private String description;

    @JsonProperty("node_id")
    private String nodeId;

    private String url;

    @JsonProperty("html_url")
    private String htmlUrl;

    @JsonProperty("repos_url")
    private String reposUrl;

    @JsonProperty("public_repos")
    private Integer publicRepos;
}