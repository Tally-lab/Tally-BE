package com.tally.service;

import com.tally.domain.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ContributionAnalysisService {
    private final GitHubService gitHubService;

    /**
     * 레포지토리 기여도 분석
     */
    public ContributionStats analyzeContribution(String token, String owner, String repo, String username) {
        // 1. 커밋 데이터 수집
        List<Commit> commits = gitHubService.getRepositoryCommits(token, owner, repo);

        // 2. PR 데이터 수집
        List<PullRequest> pullRequests = gitHubService.getRepositoryPullRequests(token, owner, repo);

        // 3. Issue 데이터 수집
        List<Issue> issues = gitHubService.getRepositoryIssues(token, owner, repo);

        // 4. 커밋 통계 계산
        long totalCommits = commits.size();
        long userCommits = commits.stream()
                .filter(commit -> commit.getCommit() != null
                        && commit.getCommit().getAuthor() != null
                        && username.equals(commit.getCommit().getAuthor().getName()))
                .count();

        double commitPercentage = totalCommits > 0
                ? (double) userCommits / totalCommits * 100
                : 0.0;

        // 5. ContributionStats 생성 (Builder 패턴 사용)
        ContributionStats stats = ContributionStats.builder()
                .id(java.util.UUID.randomUUID().toString())
                .userId(username)
                .repositoryFullName(owner + "/" + repo)
                .totalCommits((int) totalCommits)
                .userCommits((int) userCommits)
                .commitPercentage(commitPercentage)
                .analyzedAt(LocalDateTime.now())
                .build();

        log.info("Contribution analysis completed for {}/{} - User: {}, Commits: {}/{}, PRs: {}, Issues: {}",
                owner, repo, username, userCommits, totalCommits, pullRequests.size(), issues.size());

        return stats;
    }

    /**
     * 사용자의 PR 목록 조회
     */
    public List<PullRequest> getUserPullRequests(String token, String owner, String repo, String username) {
        List<PullRequest> pullRequests = gitHubService.getRepositoryPullRequests(token, owner, repo);

        return pullRequests.stream()
                .filter(pr -> pr.getUser() != null && username.equals(pr.getUser().getUsername()))
                .collect(Collectors.toList());
    }

    /**
     * 사용자의 Issue 목록 조회
     */
    public List<Issue> getUserIssues(String token, String owner, String repo, String username) {
        List<Issue> issues = gitHubService.getRepositoryIssues(token, owner, repo);

        return issues.stream()
                .filter(issue -> issue.getUser() != null && username.equals(issue.getUser().getUsername()))
                .collect(Collectors.toList());
    }
}