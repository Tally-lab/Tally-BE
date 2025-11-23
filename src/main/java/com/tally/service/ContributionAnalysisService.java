package com.tally.service;

import com.tally.domain.*;
import lombok.extern.slf4j.Slf4j;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
public class ContributionAnalysisService {
    private final GitHubService gitHubService;

    public ContributionAnalysisService() {
        this.gitHubService = new GitHubService();
    }

    public ContributionAnalysisService(GitHubService gitHubService) {
        this.gitHubService = gitHubService;
    }

    /**
     * 레포지토리 기여도 분석
     */
    public ContributionStats analyzeContribution(String token, String owner, String repo, String username) {
        log.info("Analyzing contribution for user {} in repo {}/{}", username, owner, repo);

        // 1. 커밋 데이터 수집
        List<Commit> commits = gitHubService.getRepositoryCommits(token, owner, repo);

        // 2. PR 데이터 수집
        List<PullRequest> allPRs = gitHubService.getRepositoryPullRequests(token, owner, repo);

        // 사용자의 PR만 필터링
        List<PullRequest> userPRs = allPRs.stream()
                .filter(pr -> {
                    if (pr.getUser() != null && pr.getUser().getLogin() != null) {
                        return username.equalsIgnoreCase(pr.getUser().getLogin());
                    }
                    return false;
                })
                .collect(Collectors.toList());

        // 3. Issue 데이터 수집
        List<Issue> allIssues = gitHubService.getRepositoryIssues(token, owner, repo);

        // 사용자의 Issue만 필터링
        List<Issue> userIssues = allIssues.stream()
                .filter(issue -> {
                    if (issue.getUser() != null && issue.getUser().getLogin() != null) {
                        return username.equalsIgnoreCase(issue.getUser().getLogin());
                    }
                    return false;
                })
                .collect(Collectors.toList());

        // 4. 커밋 통계 계산
        long totalCommits = commits.size();
        List<Commit> userCommitList = commits.stream()
                .filter(commit -> {
                    if (commit.getCommit() != null && commit.getCommit().getAuthor() != null) {
                        String authorName = commit.getCommit().getAuthor().getName();
                        return authorName != null && username.equalsIgnoreCase(authorName);
                    }
                    return false;
                })
                .collect(Collectors.toList());

        long userCommits = userCommitList.size();

        // 4.1 활동 기간 계산 (사용자 커밋 날짜 기준)
        String firstCommitDate = null;
        String lastCommitDate = null;
        if (!userCommitList.isEmpty()) {
            List<String> commitDates = userCommitList.stream()
                    .filter(c -> c.getCommit() != null && c.getCommit().getAuthor() != null
                            && c.getCommit().getAuthor().getDate() != null)
                    .map(c -> c.getCommit().getAuthor().getDate().substring(0, 10)) // YYYY-MM-DD
                    .sorted()
                    .collect(Collectors.toList());

            if (!commitDates.isEmpty()) {
                firstCommitDate = commitDates.get(0);
                lastCommitDate = commitDates.get(commitDates.size() - 1);
            }
        }

        double commitPercentage = totalCommits > 0
                ? (double) userCommits / totalCommits * 100
                : 0.0;

        // 4.5 커밋 메시지 추출 (AI 분석용, 최근 30개)
        List<String> commitMessages = userCommitList.stream()
                .filter(c -> c.getCommit() != null && c.getCommit().getMessage() != null)
                .map(c -> c.getCommit().getMessage().split("\n")[0]) // 첫 줄만 (제목)
                .limit(30)
                .collect(Collectors.toList());

        // 5. 역할 분석 수행
        Map<String, ContributionStats.RoleStats> roleDistribution = analyzeRoles(
                token, owner, repo, username, userCommitList
        );

        // 6. ContributionStats 생성 (Builder 패턴 사용)
        ContributionStats stats = ContributionStats.builder()
                .id(UUID.randomUUID().toString())
                .userId(username)
                .username(username)
                .repositoryFullName(owner + "/" + repo)
                .firstCommitDate(firstCommitDate)
                .lastCommitDate(lastCommitDate)
                .totalCommits((int) totalCommits)
                .userCommits((int) userCommits)
                .commitPercentage(commitPercentage)
                .roleDistribution(roleDistribution)
                .pullRequests(userPRs)
                .issues(userIssues)
                .commitMessages(commitMessages)
                .analyzedAt(LocalDateTime.now())
                .build();

        log.info("Contribution analysis completed for {}/{} - User: {}, Commits: {}/{} ({}%), PRs: {}, Issues: {}, Roles: {}",
                owner, repo, username, userCommits, totalCommits,
                String.format("%.1f", commitPercentage),
                userPRs.size(), userIssues.size(), roleDistribution.keySet());

        return stats;
    }

    /**
     * 역할 분석 (성능 최적화: 최근 20개 커밋만 분석)
     */
    private Map<String, ContributionStats.RoleStats> analyzeRoles(
            String token, String owner, String repo, String username, List<Commit> userCommits) {

        Map<String, Integer> roleCommitCount = new HashMap<>();
        Map<String, List<String>> roleFileExamples = new HashMap<>(); // 파일 예시 저장
        int totalAnalyzedCommits = 0;

        // 성능 최적화: 최근 20개 커밋만 상세 분석
        List<Commit> commitsToAnalyze = userCommits.size() > 20
            ? userCommits.subList(0, 20)
            : userCommits;

        log.info("Analyzing {} commits out of {} total (performance optimization)",
            commitsToAnalyze.size(), userCommits.size());

        for (Commit commit : commitsToAnalyze) {
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

                // 파일 예시 수집 (각 역할당 최대 5개)
                roleFileExamples.computeIfAbsent(role, k -> new ArrayList<>());
                if (roleFileExamples.get(role).size() < 5) {
                    roleFileExamples.get(role).add(file.getFilename());
                }
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

        log.info("Role analysis completed: {} roles analyzed from {} commits",
                roleDistribution.size(), totalAnalyzedCommits);

        // 커밋이 5개 이하면 각 역할별 파일 예시 로깅
        if (totalAnalyzedCommits <= 5) {
            log.info("=== Detailed role file breakdown (total commits: {}) ===", totalAnalyzedCommits);
            for (Map.Entry<String, List<String>> entry : roleFileExamples.entrySet()) {
                log.info("  [{}] files: {}", entry.getKey(), entry.getValue());
            }
        }

        return roleDistribution;
    }

    /**
     * 파일 경로를 기반으로 역할 분류
     * 백엔드/프론트엔드 상호 배타적 체크 강화
     */
    private String categorizeFile(String filename) {
        String lowerFilename = filename.toLowerCase();

        // 1순위: 설정 파일 (가장 먼저 체크)
        if (lowerFilename.endsWith("package.json") ||
                lowerFilename.endsWith("package-lock.json") ||
                lowerFilename.endsWith("yarn.lock") ||
                lowerFilename.endsWith("pom.xml") ||
                lowerFilename.endsWith("build.gradle") ||
                lowerFilename.endsWith("settings.gradle") ||
                lowerFilename.endsWith("gradle.properties") ||
                lowerFilename.endsWith(".properties") ||
                lowerFilename.endsWith(".env") ||
                lowerFilename.endsWith("vite.config.ts") ||
                lowerFilename.endsWith("vite.config.js") ||
                lowerFilename.endsWith("tsconfig.json") ||
                lowerFilename.endsWith("webpack.config.js")) {
            return "configuration";
        }

        // 2순위: 문서
        if (lowerFilename.endsWith(".md") ||
                lowerFilename.contains("readme") ||
                lowerFilename.contains("/docs/") ||
                lowerFilename.contains("/documentation/")) {
            return "documentation";
        }

        // 3순위: 인프라/DevOps
        if (lowerFilename.contains("dockerfile") ||
                lowerFilename.contains("docker-compose") ||
                lowerFilename.contains(".github/workflows/") ||
                lowerFilename.endsWith(".yml") ||
                lowerFilename.endsWith(".yaml") ||
                lowerFilename.contains("/terraform/") ||
                lowerFilename.contains("/kubernetes/") ||
                lowerFilename.contains("/k8s/")) {
            return "infrastructure";
        }

        // 4순위: 테스트
        if (lowerFilename.contains("__tests__/") ||
                lowerFilename.contains("/test/") ||
                lowerFilename.contains("/tests/") ||
                lowerFilename.endsWith(".test.js") ||
                lowerFilename.endsWith(".test.ts") ||
                lowerFilename.endsWith(".test.jsx") ||
                lowerFilename.endsWith(".test.tsx") ||
                lowerFilename.endsWith(".spec.js") ||
                lowerFilename.endsWith(".spec.ts") ||
                lowerFilename.endsWith(".spec.tsx")) {
            return "test";
        }

        // 5순위: 백엔드 체크 (프론트엔드보다 먼저)
        // Java/Spring Boot 백엔드 명확한 경로
        if (lowerFilename.contains("/src/main/java/") ||
                lowerFilename.contains("/src/main/kotlin/") ||
                lowerFilename.contains("/src/main/resources/")) {
            return "backend";
        }

        // Java 파일이 명확한 백엔드 구조에 있는 경우
        if (lowerFilename.endsWith(".java") || lowerFilename.endsWith(".kt")) {
            return "backend";
        }

        // Python/Go/Ruby 백엔드
        if ((lowerFilename.endsWith(".py") ||
                lowerFilename.endsWith(".go") ||
                lowerFilename.endsWith(".rb")) &&
                (lowerFilename.contains("/backend/") ||
                        lowerFilename.contains("/server/") ||
                        lowerFilename.contains("/api/"))) {
            return "backend";
        }

        // 6순위: 프론트엔드 체크 (백엔드가 아닌 경우만)
        // 명확한 프론트엔드 프로젝트 디렉토리 구조
        if (lowerFilename.contains("/src/") &&
                !lowerFilename.contains("/src/main/") && // Spring Boot 제외
                !lowerFilename.contains("/src/test/") && // Java test 제외
                (lowerFilename.endsWith(".ts") ||
                        lowerFilename.endsWith(".tsx") ||
                        lowerFilename.endsWith(".jsx") ||
                        lowerFilename.endsWith(".js") ||
                        lowerFilename.endsWith(".vue"))) {
            return "frontend";
        }

        // React/Vue/Frontend 특화 디렉토리
        if (lowerFilename.contains("/components/") ||
                lowerFilename.contains("/pages/") ||
                lowerFilename.contains("/views/") ||
                lowerFilename.contains("/hooks/") ||
                lowerFilename.contains("/styles/") ||
                lowerFilename.contains("/assets/") ||
                lowerFilename.contains("/public/") ||
                lowerFilename.endsWith(".css") ||
                lowerFilename.endsWith(".scss") ||
                lowerFilename.endsWith(".sass") ||
                lowerFilename.endsWith(".less")) {
            return "frontend";
        }

        // HTML은 프론트엔드 프로젝트에만 해당 (Spring templates 제외)
        if (lowerFilename.endsWith(".html") &&
                !lowerFilename.contains("/templates/") &&
                !lowerFilename.contains("/static/")) {
            return "frontend";
        }

        return "other";
    }

    /**
     * 사용자의 PR 목록 조회 (ReportGenerationService에서 사용)
     */
    public List<PullRequest> getUserPullRequests(String token, String owner, String repo, String username) {
        List<PullRequest> pullRequests = gitHubService.getRepositoryPullRequests(token, owner, repo);

        return pullRequests.stream()
                .filter(pr -> {
                    if (pr.getUser() != null && pr.getUser().getLogin() != null) {
                        return username.equalsIgnoreCase(pr.getUser().getLogin());
                    }
                    return false;
                })
                .collect(Collectors.toList());
    }

    /**
     * 사용자의 Issue 목록 조회 (ReportGenerationService에서 사용)
     */
    public List<Issue> getUserIssues(String token, String owner, String repo, String username) {
        List<Issue> issues = gitHubService.getRepositoryIssues(token, owner, repo);

        return issues.stream()
                .filter(issue -> {
                    if (issue.getUser() != null && issue.getUser().getLogin() != null) {
                        return username.equalsIgnoreCase(issue.getUser().getLogin());
                    }
                    return false;
                })
                .collect(Collectors.toList());
    }
}