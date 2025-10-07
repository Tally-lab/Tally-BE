package com.tally.domain;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
public class Issue {
    private Long number;
    private String title;
    private String state;  // open, closed

    @JsonProperty("html_url")
    private String htmlUrl;

    @JsonProperty("created_at")
    private LocalDateTime createdAt;

    @JsonProperty("closed_at")
    private LocalDateTime closedAt;

    private User user;  // Issue 작성자

    private String body;  // Issue 설명
}