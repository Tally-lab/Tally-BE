package com.tally.lambda;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestStreamHandler;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.tally.domain.*;
import com.tally.service.*;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * Lambda Response Streaming Handler for Organization Analysis
 * SSE 형식으로 실시간 진행률을 전송
 */
public class StreamingAnalysisHandler implements RequestStreamHandler {

    private final ObjectMapper objectMapper;
    private final GitHubService gitHubService;
    private final ContributionAnalysisService analysisService;

    public StreamingAnalysisHandler() {
        this.objectMapper = new ObjectMapper();
        this.objectMapper.registerModule(new JavaTimeModule());
        this.gitHubService = new GitHubService();
        this.analysisService = new ContributionAnalysisService(gitHubService);
    }

    @Override
    public void handleRequest(InputStream inputStream, OutputStream outputStream, Context context) throws IOException {
        // 요청 파싱
        JsonNode request = objectMapper.readTree(inputStream);

        String token = extractToken(request);
        String orgName = extractOrgName(request);
        String username = extractUsername(request);

        context.getLogger().log("Starting streaming analysis for org: " + orgName + ", user: " + username);

        BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(outputStream, StandardCharsets.UTF_8));

        try {
            // 1. 레포지토리 목록 가져오기
            sendEvent(writer, "status", Map.of(
                "phase", "fetching_repos",
                "message", "레포지토리 목록을 가져오는 중..."
            ));

            List<GitHubRepository> repos = gitHubService.getOrganizationRepositories(token, orgName);
            int totalRepos = repos.size();

            sendEvent(writer, "repos_loaded", Map.of(
                "total", totalRepos,
                "repos", repos.stream().map(r -> Map.of(
                    "name", r.getName(),
                    "fullName", r.getFullName() != null ? r.getFullName() : orgName + "/" + r.getName()
                )).toList()
            ));

            // 2. 각 레포지토리 분석 (진행률 전송)
            List<Map<String, Object>> repoContributions = new ArrayList<>();
            Map<String, TeamMemberData> teamMemberMap = new HashMap<>();

            int totalCommits = 0;
            int userCommits = 0;
            int totalPRs = 0;
            int totalIssues = 0;

            for (int i = 0; i < repos.size(); i++) {
                GitHubRepository repo = repos.get(i);
                int progress = (int) ((i + 1) * 100.0 / totalRepos);

                // 진행률 이벤트 전송
                sendEvent(writer, "progress", Map.of(
                    "currentRepo", repo.getName(),
                    "processed", i + 1,
                    "total", totalRepos,
                    "percentage", progress
                ));

                try {
                    // 커밋 분석
                    List<Commit> commits = gitHubService.getRepositoryCommits(token, orgName, repo.getName());

                    // 팀원별 커밋 집계
                    for (Commit commit : commits) {
                        final String authorLogin;
                        final String authorAvatar;

                        if (commit.getAuthor() != null && commit.getAuthor().getLogin() != null) {
                            authorLogin = commit.getAuthor().getLogin();
                            authorAvatar = commit.getAuthor().getAvatarUrl();
                        } else if (commit.getCommit() != null && commit.getCommit().getAuthor() != null) {
                            authorLogin = commit.getCommit().getAuthor().getName();
                            authorAvatar = null;
                        } else {
                            authorLogin = null;
                            authorAvatar = null;
                        }

                        if (authorLogin != null && !authorLogin.isEmpty()) {
                            TeamMemberData member = teamMemberMap.computeIfAbsent(authorLogin,
                                k -> new TeamMemberData(authorLogin, authorAvatar));
                            member.incrementCommits();
                        }
                    }

                    // 사용자 커밋 수 계산
                    long repoUserCommits = commits.stream()
                        .filter(commit -> isUserCommit(commit, username))
                        .count();

                    int repoTotalCommits = commits.size();
                    totalCommits += repoTotalCommits;
                    userCommits += repoUserCommits;

                    // PR, Issue 가져오기
                    List<PullRequest> prs = gitHubService.getRepositoryPullRequests(token, orgName, repo.getName());
                    List<Issue> issues = gitHubService.getRepositoryIssues(token, orgName, repo.getName());
                    totalPRs += prs.size();
                    totalIssues += issues.size();

                    // 레포지토리 기여도 계산
                    double repoPercentage = repoTotalCommits > 0
                        ? (repoUserCommits * 100.0 / repoTotalCommits)
                        : 0;

                    Map<String, Object> repoContribution = new HashMap<>();
                    repoContribution.put("name", repo.getName());
                    repoContribution.put("fullName", repo.getFullName() != null ? repo.getFullName() : orgName + "/" + repo.getName());
                    repoContribution.put("url", repo.getUrl() != null ? repo.getUrl() : "");
                    repoContribution.put("totalCommits", repoTotalCommits);
                    repoContribution.put("userCommits", (int) repoUserCommits);
                    repoContribution.put("contributionPercentage", Math.round(repoPercentage * 10.0) / 10.0);
                    repoContribution.put("pullRequests", prs.size());
                    repoContribution.put("issues", issues.size());

                    repoContributions.add(repoContribution);

                    // 레포 분석 완료 이벤트
                    sendEvent(writer, "repo_analyzed", Map.of(
                        "repo", repo.getName(),
                        "commits", repoTotalCommits,
                        "userCommits", (int) repoUserCommits,
                        "prs", prs.size(),
                        "issues", issues.size()
                    ));

                } catch (Exception e) {
                    context.getLogger().log("Error analyzing repo " + repo.getName() + ": " + e.getMessage());
                    sendEvent(writer, "repo_error", Map.of(
                        "repo", repo.getName(),
                        "error", e.getMessage()
                    ));
                }
            }

            // 3. 최종 결과 계산
            double overallPercentage = totalCommits > 0
                ? (userCommits * 100.0 / totalCommits)
                : 0;

            // 기여도 순 정렬
            repoContributions.sort((a, b) -> Double.compare(
                (Double) b.get("contributionPercentage"),
                (Double) a.get("contributionPercentage")
            ));

            // 팀원 리스트 생성 (커밋 순 정렬)
            final int finalTotalCommits = totalCommits;
            List<Map<String, Object>> teamMembers = teamMemberMap.values().stream()
                .map(member -> {
                    Map<String, Object> m = new HashMap<>();
                    m.put("login", member.login);
                    m.put("avatarUrl", member.avatarUrl);
                    m.put("commits", member.commits);
                    m.put("contributionPercentage", finalTotalCommits > 0
                        ? Math.round(member.commits * 1000.0 / finalTotalCommits) / 10.0
                        : 0);
                    return m;
                })
                .sorted((a, b) -> Integer.compare((Integer) b.get("commits"), (Integer) a.get("commits")))
                .toList();

            // 기여한 레포지토리 수
            long contributedRepoCount = repoContributions.stream()
                .filter(r -> (Integer) r.get("userCommits") > 0)
                .count();

            // 4. 완료 이벤트 전송
            Map<String, Object> result = new HashMap<>();
            result.put("organizationName", orgName);
            result.put("totalRepositories", (int) contributedRepoCount);
            result.put("totalCommits", totalCommits);
            result.put("userCommits", userCommits);
            result.put("contributionPercentage", Math.round(overallPercentage * 10.0) / 10.0);
            result.put("totalPullRequests", totalPRs);
            result.put("totalIssues", totalIssues);
            result.put("repositories", repoContributions);
            result.put("teamMembers", teamMembers);

            sendEvent(writer, "complete", Map.of(
                "success", true,
                "result", result
            ));

            context.getLogger().log("Analysis complete for " + orgName + ": " + totalCommits + " commits, " + teamMembers.size() + " members");

        } catch (Exception e) {
            context.getLogger().log("Error: " + e.getMessage());
            sendEvent(writer, "error", Map.of(
                "message", e.getMessage()
            ));
        } finally {
            writer.flush();
        }
    }

    /**
     * SSE 형식으로 이벤트 전송
     */
    private void sendEvent(BufferedWriter writer, String eventType, Map<String, Object> data) throws IOException {
        writer.write("event: " + eventType + "\n");
        writer.write("data: " + objectMapper.writeValueAsString(data) + "\n\n");
        writer.flush();
    }

    private String extractToken(JsonNode request) {
        // Function URL은 headers가 다르게 들어옴
        JsonNode headers = request.get("headers");
        if (headers != null) {
            JsonNode auth = headers.get("authorization");
            if (auth == null) auth = headers.get("Authorization");
            if (auth != null) {
                String authStr = auth.asText();
                if (authStr.startsWith("Bearer ")) {
                    return authStr.substring(7);
                }
                return authStr;
            }
        }

        // Query string에서도 확인
        JsonNode queryParams = request.get("queryStringParameters");
        if (queryParams != null && queryParams.has("token")) {
            return queryParams.get("token").asText();
        }

        return null;
    }

    private String extractOrgName(JsonNode request) {
        // Path parameter에서 추출
        JsonNode pathParams = request.get("pathParameters");
        if (pathParams != null && pathParams.has("orgName")) {
            return pathParams.get("orgName").asText();
        }

        // Query string에서도 확인
        JsonNode queryParams = request.get("queryStringParameters");
        if (queryParams != null && queryParams.has("org")) {
            return queryParams.get("org").asText();
        }

        return null;
    }

    private String extractUsername(JsonNode request) {
        JsonNode queryParams = request.get("queryStringParameters");
        if (queryParams != null && queryParams.has("username")) {
            return queryParams.get("username").asText();
        }
        return null;
    }

    private boolean isUserCommit(Commit commit, String username) {
        if (username == null || username.isEmpty()) return false;

        if (commit.getAuthor() != null && commit.getAuthor().getLogin() != null) {
            return username.equalsIgnoreCase(commit.getAuthor().getLogin());
        }
        if (commit.getCommit() != null && commit.getCommit().getAuthor() != null) {
            String authorName = commit.getCommit().getAuthor().getName();
            return authorName != null && authorName.equalsIgnoreCase(username);
        }
        return false;
    }

    /**
     * 팀원 데이터 저장용 내부 클래스
     */
    private static class TeamMemberData {
        String login;
        String avatarUrl;
        int commits;

        TeamMemberData(String login, String avatarUrl) {
            this.login = login;
            this.avatarUrl = avatarUrl;
            this.commits = 0;
        }

        void incrementCommits() {
            this.commits++;
        }
    }
}
