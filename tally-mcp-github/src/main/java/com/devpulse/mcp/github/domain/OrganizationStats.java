package com.devpulse.mcp.github.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrganizationStats {
    private String organizationName;
    private String avatarUrl;
    private String description;

    private int totalRepositories;
    private int totalCommits;
    private int userCommits;
    private double contributionPercentage;

    private List<RepositoryContribution> repositories;

    private int totalIssues;
    private int totalPullRequests;

    private List<TeamMember> teamMembers;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RepositoryContribution {
        private String name;
        private String fullName;
        private String url;
        private int totalCommits;
        private int userCommits;
        private double contributionPercentage;
        private String lastUpdated;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TeamMember {
        private String login;
        private String avatarUrl;
        private int commits;
        private double contributionPercentage;
    }
}
