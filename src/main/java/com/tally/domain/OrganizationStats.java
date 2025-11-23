package com.tally.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrganizationStats {
    private String organizationName;
    private String avatarUrl;
    private String description;

    // 전체 통계
    private int totalRepositories;
    private int totalCommits;
    private int userCommits;
    private double contributionPercentage;

    // 레포지토리별 상세
    private List<RepositoryContribution> repositories;

    // 활동 통계
    private int totalIssues;
    private int totalPullRequests;

    // 팀원별 기여도
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