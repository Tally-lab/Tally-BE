package com.devpulse.agent.dto;

import java.util.List;
import java.util.Map;

public record ProjectOverviewResponse(
        RepoInfo repo,
        List<ContributorInfo> contributors,
        HealthIndicators health,
        ActivitySummary recentActivity,
        Map<String, Double> languages,
        TechStackInfo techStack
) {

    public record RepoInfo(
            String fullName,
            String description,
            String defaultBranch,
            int stars,
            int forks,
            int openIssues
    ) {}

    public record ContributorInfo(
            String login,
            String avatarUrl,
            int commits,
            double percentage
    ) {}

    public record HealthIndicators(
            BusFactorInfo busFactor,
            ReviewStatus review
    ) {}

    public record BusFactorInfo(
            int score,
            String status
    ) {}

    public record ReviewStatus(
            int openPRs,
            List<PullRequestInfo> pendingPRs
    ) {}

    public record PullRequestInfo(
            int number,
            String title,
            String author,
            String createdAt
    ) {}

    public record ActivitySummary(
            int commits7d,
            int prsOpened7d,
            int prsMerged7d
    ) {}

    public record TechStackInfo(
            int totalCount,
            List<TechCategory> categories
    ) {}

    public record TechCategory(
            String category,
            List<TechItem> items
    ) {}

    public record TechItem(
            String name,
            String version,
            String docsUrl,
            String source
    ) {}
}
