package com.devpulse.mcp.github.domain;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
public class PullRequest {
    private Long number;
    private String title;
    private String state;

    @JsonProperty("html_url")
    private String htmlUrl;

    @JsonProperty("created_at")
    private LocalDateTime createdAt;

    @JsonProperty("closed_at")
    private LocalDateTime closedAt;

    @JsonProperty("merged_at")
    private LocalDateTime mergedAt;

    private User user;
    private String body;
}
