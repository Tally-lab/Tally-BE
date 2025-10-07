package com.tally.service;

import com.tally.domain.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;
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
        List<Commit> userCommitList = commits.stream()
                .filter(commit -> commit.getCommit() != null
                        && commit.getCommit().getAuthor() != null
                        && username.equals(commit.getCommit().getAuthor().getName()))
                .collect(Collectors.toList());

        long userCommits = userCommitList.size();

        double commitPercentage = totalCommits > 0
                ? (double) userCommits / totalCommits * 100
                : 0.0;

        // 5. 역할 분석 수행
        Map<String, ContributionStats.RoleStats> roleDistribution = analyzeRoles(
                token, owner, repo, username, userCommitList
        );

        // 6. ContributionStats 생성 (Builder 패턴 사용)
        ContributionStats stats = ContributionStats.builder()
                .id(java.util.UUID.randomUUID().toString())
                .userId(username)
                .repositoryFullName(owner + "/" + repo)
                .totalCommits((int) totalCommits)
                .userCommits((int) userCommits)
                .commitPercentage(commitPercentage)
                .roleDistribution(roleDistribution)
                .analyzedAt(LocalDateTime.now())
                .build();

        log.info("Contribution analysis completed for {}/{} - User: {}, Commits: {}/{}, Roles: {}",
                owner, repo, username, userCommits, totalCommits, roleDistribution.keySet());

        return stats;
    }

    /**
     * 역할 분석
     */
    private Map<String, ContributionStats.RoleStats> analyzeRoles(
            String token, String owner, String repo, String username, List<Commit> userCommits) {

        Map<String, Integer> roleCommitCount = new HashMap<>();
        int totalAnalyzedCommits = 0;

        for (Commit commit : userCommits) {
            // 커밋 상세 정보 조회 (파일 목록 포함)
            Commit detailedCommit = gitHubService.getCommitDetail(token, owner, repo, commit.getSha());

            if (detailedCommit == null || detailedCommit.getFiles() == null) {
                continue;
            }

            totalAnalyzedCommits++;

            // 이 커밋에서 변경된 파일들을 역할로 분류
            Set<String> rolesInCommit = new HashSet<>();

            for (Commit.CommitFile file : detailedCommit.getFiles()) {
                String role = categorizeFile(file.getFilename());
                rolesInCommit.add(role);
            }

            // 각 역할에 커밋 카운트 추가
            for (String role : rolesInCommit) {
                roleCommitCount.put(role, roleCommitCount.getOrDefault(role, 0) + 1);
            }
        }

        // RoleStats 객체로 변환
        Map<String, ContributionStats.RoleStats> roleDistribution = new HashMap<>();

        for (Map.Entry<String, Integer> entry : roleCommitCount.entrySet()) {
            String roleName = entry.getKey();
            int commitCount = entry.getValue();
            double percentage = totalAnalyzedCommits > 0
                    ? (double) commitCount / totalAnalyzedCommits * 100
                    : 0.0;

            roleDistribution.put(roleName, ContributionStats.RoleStats.builder()
                    .roleName(roleName)
                    .commitCount(commitCount)
                    .percentage(percentage)
                    .build());
        }

        return roleDistribution;
    }

    /**
     * 파일 경로를 기반으로 역할 분류
     */
    private String categorizeFile(String filename) {
        String lowerFilename = filename.toLowerCase();

        // 백엔드
        if (lowerFilename.contains("src/main/java") ||
                lowerFilename.endsWith(".java") ||
                lowerFilename.contains("controller") ||
                lowerFilename.contains("service") ||
                lowerFilename.contains("repository") ||
                lowerFilename.contains("domain")) {
            return "백엔드";
        }

        // 프론트엔드
        if (lowerFilename.contains("src/components") ||
                lowerFilename.contains("src/pages") ||
                lowerFilename.endsWith(".jsx") ||
                lowerFilename.endsWith(".tsx") ||
                lowerFilename.endsWith(".vue") ||
                lowerFilename.contains("frontend")) {
            return "프론트엔드";
        }

        // 인프라/DevOps
        if (lowerFilename.contains("dockerfile") ||
                lowerFilename.contains("docker-compose") ||
                lowerFilename.contains(".github/workflows") ||
                lowerFilename.contains("terraform") ||
                lowerFilename.contains(".yml") ||
                lowerFilename.contains(".yaml")) {
            return "인프라";
        }

        // 테스트
        if (lowerFilename.contains("test") ||
                lowerFilename.contains("spec")) {
            return "테스트";
        }

        // 문서
        if (lowerFilename.endsWith(".md") ||
                lowerFilename.contains("readme") ||
                lowerFilename.contains("docs/")) {
            return "문서";
        }

        // 설정
        if (lowerFilename.contains("config") ||
                lowerFilename.contains("gradle") ||
                lowerFilename.contains("pom.xml") ||
                lowerFilename.contains("package.json")) {
            return "설정";
        }

        return "기타";
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