package com.tally.controller;

import com.tally.domain.*;
import com.tally.service.GitHubService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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
        log.info("Fetching stats for organization: {}, username: {}", orgName, username);

        // 조직의 모든 레포지토리 가져오기
        List<GitHubRepository> orgRepos = gitHubService.getOrganizationRepositories(accessToken, orgName);

        int totalCommits = 0;
        int userCommits = 0;
        int totalIssues = 0;
        int totalPullRequests = 0;

        List<OrganizationStats.RepositoryContribution> repoContributions = new ArrayList<>();

        // 팀원별 커밋 수 추적 (login -> [commits, avatarUrl])
        Map<String, int[]> memberCommits = new HashMap<>();
        Map<String, String> memberAvatars = new HashMap<>();

        // 각 레포지토리별 통계 계산
        for (GitHubRepository repo : orgRepos) {
            try {
                List<Commit> commits = gitHubService.getRepositoryCommits(accessToken, orgName, repo.getName());

                // 팀원별 커밋 수집
                for (Commit commit : commits) {
                    String authorLogin = null;
                    String authorAvatar = null;

                    if (commit.getAuthor() != null && commit.getAuthor().getLogin() != null) {
                        authorLogin = commit.getAuthor().getLogin();
                        authorAvatar = commit.getAuthor().getAvatarUrl();
                    } else if (commit.getCommit() != null && commit.getCommit().getAuthor() != null) {
                        authorLogin = commit.getCommit().getAuthor().getName();
                    }

                    if (authorLogin != null && !authorLogin.isEmpty()) {
                        memberCommits.computeIfAbsent(authorLogin, k -> new int[1])[0]++;
                        if (authorAvatar != null) {
                            memberAvatars.putIfAbsent(authorLogin, authorAvatar);
                        }
                    }
                }

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

                // 모든 레포지토리를 포함 (기여도 0%도 포함)
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

        // 기여도 순으로 정렬 (높은 기여도부터)
        repoContributions.sort((a, b) -> Double.compare(b.getContributionPercentage(), a.getContributionPercentage()));

        // 기여한 레포지토리 수 계산 (userCommits > 0)
        long contributedRepoCount = repoContributions.stream()
                .filter(r -> r.getUserCommits() > 0)
                .count();

        // 팀원 리스트 생성 (커밋 수 기준 내림차순)
        final int finalTotalCommits = totalCommits;
        List<OrganizationStats.TeamMember> teamMembers = memberCommits.entrySet().stream()
                .map(entry -> OrganizationStats.TeamMember.builder()
                        .login(entry.getKey())
                        .avatarUrl(memberAvatars.get(entry.getKey()))
                        .commits(entry.getValue()[0])
                        .contributionPercentage(finalTotalCommits > 0
                                ? Math.round(entry.getValue()[0] * 1000.0 / finalTotalCommits) / 10.0
                                : 0.0)
                        .build())
                .sorted((a, b) -> Integer.compare(b.getCommits(), a.getCommits()))
                .toList();

        OrganizationStats stats = OrganizationStats.builder()
                .organizationName(orgName)
                .avatarUrl(avatarUrl)
                .description(description)
                .totalRepositories((int) contributedRepoCount)  // 기여한 레포지토리 수
                .totalCommits(totalCommits)
                .userCommits(userCommits)
                .contributionPercentage(Math.round(overallContributionPercentage * 10.0) / 10.0)
                .repositories(repoContributions)  // 모든 레포지토리 (0% 포함)
                .totalIssues(totalIssues)
                .totalPullRequests(totalPullRequests)
                .teamMembers(teamMembers)
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