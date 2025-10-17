package com.tally.controller;

import com.tally.domain.*;
import com.tally.service.GitHubService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;

@Slf4j
@RestController
@RequestMapping("/organizations")
@RequiredArgsConstructor
public class OrganizationController {

    private final GitHubService gitHubService;

    /**
     * 사용자가 속한 조직 목록 조회 (Grant 승인된 조직만)
     */
    @GetMapping
    public ResponseEntity<List<Organization>> getUserOrganizations(
            @RequestHeader("Authorization") String authorization) {

        String accessToken = authorization.replace("Bearer ", "");
        log.info("Fetching organizations for user");

        // ✅ API에서만 가져오기 (Grant 승인된 조직)
        List<Organization> organizations = gitHubService.getUserOrganizations(accessToken);

        log.info("Returning {} organizations", organizations.size());
        return ResponseEntity.ok(organizations);
    }

    /**
     * 특정 조직의 상세 정보 및 사용자 기여도 통계
     */
    @GetMapping("/{orgName}/stats")
    public ResponseEntity<OrganizationStats> getOrganizationStats(
            @PathVariable String orgName,
            @RequestHeader("Authorization") String authorization,
            @RequestParam(required = false) String username) {

        String accessToken = authorization.replace("Bearer ", "");
        log.info("Fetching stats for organization: {}", orgName);

        // 조직의 모든 레포지토리 가져오기
        List<GitHubRepository> orgRepos = gitHubService.getOrganizationRepositories(accessToken, orgName);

        int totalCommits = 0;
        int userCommits = 0;
        int totalIssues = 0;
        int totalPullRequests = 0;

        List<OrganizationStats.RepositoryContribution> repoContributions = new ArrayList<>();

        // 각 레포지토리별 통계 계산
        for (GitHubRepository repo : orgRepos) {
            try {
                List<Commit> commits = gitHubService.getRepositoryCommits(accessToken, orgName, repo.getName());

                long repoUserCommits = 0;
                if (username != null && !username.isEmpty()) {
                    repoUserCommits = commits.stream()
                            .filter(commit -> {
                                if (commit.getAuthor() != null && commit.getAuthor().getLogin() != null) {
                                    return username.equalsIgnoreCase(commit.getAuthor().getLogin());
                                }
                                if (commit.getCommit() != null && commit.getCommit().getAuthor() != null) {
                                    String authorName = commit.getCommit().getAuthor().getName();
                                    return authorName != null && authorName.equalsIgnoreCase(username);
                                }
                                return false;
                            })
                            .count();
                }

                int repoTotalCommits = commits.size();
                totalCommits += repoTotalCommits;
                userCommits += repoUserCommits;

                double repoContributionPercentage = repoTotalCommits > 0
                        ? (repoUserCommits * 100.0 / repoTotalCommits)
                        : 0.0;

                if (repoUserCommits > 0) {
                    OrganizationStats.RepositoryContribution contribution = OrganizationStats.RepositoryContribution.builder()
                            .name(repo.getName())
                            .fullName(repo.getFullName())
                            .url(repo.getUrl())
                            .totalCommits(repoTotalCommits)
                            .userCommits((int) repoUserCommits)
                            .contributionPercentage(Math.round(repoContributionPercentage * 10.0) / 10.0)
                            .lastUpdated(repo.getUpdatedAt())
                            .build();

                    repoContributions.add(contribution);
                }

                List<Issue> issues = gitHubService.getRepositoryIssues(accessToken, orgName, repo.getName());
                List<PullRequest> prs = gitHubService.getRepositoryPullRequests(accessToken, orgName, repo.getName());

                totalIssues += issues.size();
                totalPullRequests += prs.size();

            } catch (Exception e) {
                log.error("Error processing repository {}: {}", repo.getName(), e.getMessage());
            }
        }

        double overallContributionPercentage = totalCommits > 0
                ? (userCommits * 100.0 / totalCommits)
                : 0.0;

        String avatarUrl = "";
        String description = "";
        if (!orgRepos.isEmpty() && orgRepos.get(0).getOwner() != null) {
            avatarUrl = orgRepos.get(0).getOwner().getAvatarUrl();
        }

        OrganizationStats stats = OrganizationStats.builder()
                .organizationName(orgName)
                .avatarUrl(avatarUrl)
                .description(description)
                .totalRepositories(repoContributions.size())
                .totalCommits(totalCommits)
                .userCommits(userCommits)
                .contributionPercentage(Math.round(overallContributionPercentage * 10.0) / 10.0)
                .repositories(repoContributions)
                .totalIssues(totalIssues)
                .totalPullRequests(totalPullRequests)
                .build();

        log.info("Organization {} stats: {} repos, {}/{} commits ({}%)",
                orgName, stats.getTotalRepositories(), userCommits, totalCommits, stats.getContributionPercentage());

        return ResponseEntity.ok(stats);
    }

    /**
     * 특정 조직의 레포지토리 목록 조회
     */
    @GetMapping("/{orgName}/repositories")
    public ResponseEntity<List<GitHubRepository>> getOrganizationRepositories(
            @PathVariable String orgName,
            @RequestHeader("Authorization") String authorization) {

        String accessToken = authorization.replace("Bearer ", "");
        log.info("Fetching repositories for organization: {}", orgName);

        List<GitHubRepository> repositories = gitHubService.getOrganizationRepositories(accessToken, orgName);
        return ResponseEntity.ok(repositories);
    }
}