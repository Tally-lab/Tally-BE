package com.devpulse.mcp.github.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ContributionStats {
    private String id;
    private String userId;
    private String username;
    private String repositoryFullName;

    private String firstCommitDate;
    private String lastCommitDate;

    private int totalCommits;
    private int userCommits;
    private double commitPercentage;

    private int additions;
    private int deletions;
    private Map<String, Integer> languageDistribution;

    private Map<Integer, Integer> hourlyActivity;
    private Map<String, Integer> dailyActivity;

    private Map<String, RoleStats> roleDistribution;

    private List<PullRequest> pullRequests;
    private List<Issue> issues;

    private List<String> commitMessages;

    private LocalDateTime analyzedAt;

    @Getter
    @Setter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RoleStats {
        private String roleName;
        private int commitCount;
        private double percentage;
    }
}
