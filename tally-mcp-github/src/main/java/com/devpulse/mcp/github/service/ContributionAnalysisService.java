package com.devpulse.mcp.github.service;

import com.devpulse.mcp.github.domain.*;
import com.devpulse.mcp.github.service.GraphQLGitHubService.RepositoryData;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * GraphQL 기반 기여도 분석 서비스 (REST 의존 제거)
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ContributionAnalysisService {

    private final GraphQLGitHubService graphQLService;

    /**
     * 레포지토리 기여도 분석 (GraphQL 1회 호출)
     */
    public ContributionStats analyzeContribution(String token, String owner, String repo, String username) {
        log.info("Analyzing contribution for {} in {}/{}", username, owner, repo);

        RepositoryData repoData = graphQLService.getRepositoryAnalysis(token, owner, repo);

        // 커밋 필터링
        List<Commit> allCommits = repoData.getCommits();
        List<Commit> userCommits = allCommits.stream()
                .filter(c -> isCommitByUser(c, username))
                .collect(Collectors.toList());

        // PR 필터링
        List<PullRequest> userPRs = repoData.getPullRequests().stream()
                .filter(pr -> pr.getUser() != null && username.equalsIgnoreCase(pr.getUser().getLogin()))
                .collect(Collectors.toList());

        // Issue 필터링
        List<Issue> userIssues = repoData.getIssues().stream()
                .filter(issue -> issue.getUser() != null && username.equalsIgnoreCase(issue.getUser().getLogin()))
                .collect(Collectors.toList());

        // 활동 기간
        String firstCommitDate = null;
        String lastCommitDate = null;
        if (!userCommits.isEmpty()) {
            List<String> dates = userCommits.stream()
                    .filter(c -> c.getCommit() != null && c.getCommit().getAuthor() != null
                            && c.getCommit().getAuthor().getDate() != null)
                    .map(c -> c.getCommit().getAuthor().getDate().substring(0, 10))
                    .sorted()
                    .collect(Collectors.toList());
            if (!dates.isEmpty()) {
                firstCommitDate = dates.get(0);
                lastCommitDate = dates.get(dates.size() - 1);
            }
        }

        double commitPercentage = allCommits.isEmpty() ? 0.0
                : (double) userCommits.size() / allCommits.size() * 100;

        // 커밋 메시지 (AI 분석용, 최근 30개)
        List<String> commitMessages = userCommits.stream()
                .filter(c -> c.getCommit() != null && c.getCommit().getMessage() != null)
                .map(c -> c.getCommit().getMessage().split("\n")[0])
                .limit(30)
                .collect(Collectors.toList());

        // 역할 분석 (커밋 메시지 기반 — 파일 경로 없이도 가능)
        Map<String, ContributionStats.RoleStats> roleDistribution = analyzeRolesFromMessages(commitMessages);

        ContributionStats stats = ContributionStats.builder()
                .id(UUID.randomUUID().toString())
                .userId(username)
                .username(username)
                .repositoryFullName(owner + "/" + repo)
                .firstCommitDate(firstCommitDate)
                .lastCommitDate(lastCommitDate)
                .totalCommits(repoData.getTotalCommitCount())
                .userCommits(userCommits.size())
                .commitPercentage(commitPercentage)
                .languageDistribution(repoData.getLanguageDistribution())
                .roleDistribution(roleDistribution)
                .pullRequests(userPRs)
                .issues(userIssues)
                .commitMessages(commitMessages)
                .analyzedAt(LocalDateTime.now())
                .build();

        log.info("Analysis complete: {}/{} — User: {}, Commits: {}/{} ({}%), PRs: {}, Issues: {}",
                owner, repo, username, userCommits.size(), allCommits.size(),
                String.format("%.1f", commitPercentage), userPRs.size(), userIssues.size());

        return stats;
    }

    /**
     * 조직 전체 분석
     */
    public OrganizationStats analyzeOrganization(String token, String orgName, String username) {
        log.info("Analyzing organization {} for user {}", orgName, username);

        Map<String, RepositoryData> repoDataMap = graphQLService.getOrganizationFullAnalysis(token, orgName);

        int[] totalCommits = {0};
        int[] userCommitsTotal = {0};
        List<OrganizationStats.RepositoryContribution> repoContributions = new ArrayList<>();
        Map<String, Integer> teamCommitCounts = new HashMap<>();

        for (Map.Entry<String, RepositoryData> entry : repoDataMap.entrySet()) {
            RepositoryData data = entry.getValue();
            List<Commit> commits = data.getCommits();
            int repoTotal = commits.size();
            totalCommits[0] += repoTotal;

            for (Commit commit : commits) {
                String author = getCommitAuthorLogin(commit);
                if (author != null) {
                    teamCommitCounts.merge(author, 1, Integer::sum);
                }
            }

            long userRepoCommits = commits.stream().filter(c -> isCommitByUser(c, username)).count();
            userCommitsTotal[0] += (int) userRepoCommits;

            double percentage = repoTotal == 0 ? 0.0 : (double) userRepoCommits / repoTotal * 100;

            repoContributions.add(OrganizationStats.RepositoryContribution.builder()
                    .name(data.getName())
                    .fullName(data.getFullName())
                    .url(data.getUrl())
                    .totalCommits(repoTotal)
                    .userCommits((int) userRepoCommits)
                    .contributionPercentage(percentage)
                    .build());
        }

        final int finalTotalCommits = totalCommits[0];
        List<OrganizationStats.TeamMember> teamMembers = teamCommitCounts.entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                .map(e -> OrganizationStats.TeamMember.builder()
                        .login(e.getKey())
                        .commits(e.getValue())
                        .contributionPercentage(finalTotalCommits == 0 ? 0 : (double) e.getValue() / finalTotalCommits * 100)
                        .build())
                .collect(Collectors.toList());

        return OrganizationStats.builder()
                .organizationName(orgName)
                .totalRepositories(repoDataMap.size())
                .totalCommits(finalTotalCommits)
                .userCommits(userCommitsTotal[0])
                .contributionPercentage(finalTotalCommits == 0 ? 0 : (double) userCommitsTotal[0] / finalTotalCommits * 100)
                .repositories(repoContributions)
                .teamMembers(teamMembers)
                .build();
    }

    private boolean isCommitByUser(Commit commit, String username) {
        // GitHub login으로 먼저 체크
        if (commit.getAuthor() != null && commit.getAuthor().getLogin() != null) {
            return username.equalsIgnoreCase(commit.getAuthor().getLogin());
        }
        // Git author name으로 폴백
        if (commit.getCommit() != null && commit.getCommit().getAuthor() != null) {
            String name = commit.getCommit().getAuthor().getName();
            return name != null && username.equalsIgnoreCase(name);
        }
        return false;
    }

    private String getCommitAuthorLogin(Commit commit) {
        if (commit.getAuthor() != null && commit.getAuthor().getLogin() != null) {
            return commit.getAuthor().getLogin();
        }
        if (commit.getCommit() != null && commit.getCommit().getAuthor() != null) {
            return commit.getCommit().getAuthor().getName();
        }
        return null;
    }

    /**
     * 커밋 메시지 기반 역할 분석 (Conventional Commits)
     */
    private Map<String, ContributionStats.RoleStats> analyzeRolesFromMessages(List<String> messages) {
        Map<String, Integer> roleCounts = new HashMap<>();

        for (String msg : messages) {
            String lower = msg.toLowerCase();
            if (lower.startsWith("feat")) roleCounts.merge("feature", 1, Integer::sum);
            else if (lower.startsWith("fix")) roleCounts.merge("bugfix", 1, Integer::sum);
            else if (lower.startsWith("docs")) roleCounts.merge("documentation", 1, Integer::sum);
            else if (lower.startsWith("test")) roleCounts.merge("test", 1, Integer::sum);
            else if (lower.startsWith("refactor")) roleCounts.merge("refactoring", 1, Integer::sum);
            else if (lower.startsWith("chore") || lower.startsWith("ci") || lower.startsWith("build"))
                roleCounts.merge("infrastructure", 1, Integer::sum);
            else if (lower.startsWith("style")) roleCounts.merge("style", 1, Integer::sum);
            else if (lower.startsWith("perf")) roleCounts.merge("performance", 1, Integer::sum);
            else roleCounts.merge("other", 1, Integer::sum);
        }

        int total = messages.size();
        Map<String, ContributionStats.RoleStats> result = new HashMap<>();
        for (Map.Entry<String, Integer> entry : roleCounts.entrySet()) {
            result.put(entry.getKey(), ContributionStats.RoleStats.builder()
                    .roleName(entry.getKey())
                    .commitCount(entry.getValue())
                    .percentage(total == 0 ? 0 : (double) entry.getValue() / total * 100)
                    .build());
        }
        return result;
    }
}
