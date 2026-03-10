package com.devpulse.mcp.github.tools;

import com.devpulse.mcp.github.domain.*;
import com.devpulse.mcp.github.service.ContributionAnalysisService;
import com.devpulse.mcp.github.service.GraphQLGitHubService;
import com.devpulse.mcp.github.service.QualityAnalysisService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;

/**
 * MCP Server 도구 — 레포지토리 분석
 * tally-agent의 MCP Client가 이 도구들을 호출합니다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RepoAnalysisTools {

    private final GraphQLGitHubService graphQLService;
    private final ContributionAnalysisService contributionService;
    private final QualityAnalysisService qualityService;
    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

    @Tool(description = "사용자의 GitHub 레포지토리 목록을 조회합니다. 레포 이름, 소유자, 설명, public/private 여부를 반환합니다.")
    public String listRepos(
            @ToolParam(description = "GitHub 액세스 토큰") String token) {
        log.info("Tool: list_repos");
        List<GitHubRepository> repos = graphQLService.getUserRepositories(token);

        return repos.stream()
                .map(r -> String.format("- %s (%s) %s",
                        r.getFullName(),
                        r.getIsPrivate() ? "private" : "public",
                        r.getDescription() != null ? r.getDescription() : ""))
                .collect(Collectors.joining("\n"));
    }

    @Tool(description = "특정 레포지토리의 기여도를 분석합니다. 커밋 수, 기여 비율, PR, Issue, 역할 분석 결과를 반환합니다.")
    public String analyzeRepo(
            @ToolParam(description = "GitHub 액세스 토큰") String token,
            @ToolParam(description = "레포지토리 소유자 (예: Tally-lab)") String owner,
            @ToolParam(description = "레포지토리 이름 (예: Tally-BE)") String repo,
            @ToolParam(description = "분석 대상 GitHub 사용자명") String username) {
        log.info("Tool: analyze_repo {}/{} for {}", owner, repo, username);
        ContributionStats stats = contributionService.analyzeContribution(token, owner, repo, username);

        StringBuilder sb = new StringBuilder();
        sb.append(String.format("## %s/%s 기여도 분석 — %s\n\n", owner, repo, username));
        sb.append(String.format("- 총 커밋: %d개\n", stats.getTotalCommits()));
        sb.append(String.format("- 내 커밋: %d개 (%.1f%%)\n", stats.getUserCommits(), stats.getCommitPercentage()));
        sb.append(String.format("- 활동 기간: %s ~ %s\n", stats.getFirstCommitDate(), stats.getLastCommitDate()));
        sb.append(String.format("- PR: %d개\n", stats.getPullRequests() != null ? stats.getPullRequests().size() : 0));
        sb.append(String.format("- Issue: %d개\n", stats.getIssues() != null ? stats.getIssues().size() : 0));

        if (stats.getRoleDistribution() != null && !stats.getRoleDistribution().isEmpty()) {
            sb.append("\n### 역할 분포\n");
            stats.getRoleDistribution().entrySet().stream()
                    .sorted((a, b) -> Double.compare(b.getValue().getPercentage(), a.getValue().getPercentage()))
                    .forEach(e -> sb.append(String.format("- %s: %.1f%% (%d커밋)\n",
                            e.getKey(), e.getValue().getPercentage(), e.getValue().getCommitCount())));
        }

        if (stats.getLanguageDistribution() != null && !stats.getLanguageDistribution().isEmpty()) {
            sb.append("\n### 언어 분포\n");
            stats.getLanguageDistribution().entrySet().stream()
                    .sorted((a, b) -> Integer.compare(b.getValue(), a.getValue()))
                    .limit(5)
                    .forEach(e -> sb.append(String.format("- %s: %,d bytes\n", e.getKey(), e.getValue())));
        }

        return sb.toString();
    }

    @Tool(description = "레포지토리의 커밋 품질을 분석합니다. Conventional Commits 준수율, 커밋 타입 분포, 품질 등급(A-F)을 반환합니다.")
    public String getCommitQuality(
            @ToolParam(description = "GitHub 액세스 토큰") String token,
            @ToolParam(description = "레포지토리 소유자") String owner,
            @ToolParam(description = "레포지토리 이름") String repo) {
        log.info("Tool: get_commit_quality {}/{}", owner, repo);

        GraphQLGitHubService.RepositoryData repoData = graphQLService.getRepositoryAnalysis(token, owner, repo);
        CommitQualityMetrics metrics = qualityService.analyzeCommitQuality(repoData.getCommits());

        StringBuilder sb = new StringBuilder();
        sb.append(String.format("## %s/%s 커밋 품질 분석\n\n", owner, repo));
        sb.append(String.format("- 품질 등급: **%s**\n", metrics.getQualityGrade()));
        sb.append(String.format("- 총 커밋: %d개\n", metrics.getTotalCommits()));
        sb.append(String.format("- Conventional Commits 준수: %d/%d (%.1f%%)\n",
                metrics.getConventionalCommits(), metrics.getTotalCommits(), metrics.getConventionalCommitRate()));

        if (metrics.getCommitTypeDistribution() != null && !metrics.getCommitTypeDistribution().isEmpty()) {
            sb.append("\n### 커밋 타입 분포\n");
            metrics.getCommitTypeDistribution().entrySet().stream()
                    .sorted((a, b) -> Integer.compare(b.getValue(), a.getValue()))
                    .forEach(e -> sb.append(String.format("- %s: %d개\n", e.getKey(), e.getValue())));
        }

        return sb.toString();
    }

    @Tool(description = "레포지토리의 PR 품질을 분석합니다. 머지율, 평균 리뷰 시간, 품질 등급(A-F)을 반환합니다.")
    public String getPrQuality(
            @ToolParam(description = "GitHub 액세스 토큰") String token,
            @ToolParam(description = "레포지토리 소유자") String owner,
            @ToolParam(description = "레포지토리 이름") String repo) {
        log.info("Tool: get_pr_quality {}/{}", owner, repo);

        GraphQLGitHubService.RepositoryData repoData = graphQLService.getRepositoryAnalysis(token, owner, repo);
        PRQualityMetrics metrics = qualityService.analyzePRQuality(repoData.getPullRequests());

        StringBuilder sb = new StringBuilder();
        sb.append(String.format("## %s/%s PR 품질 분석\n\n", owner, repo));
        sb.append(String.format("- 품질 등급: **%s**\n", metrics.getQualityGrade()));
        sb.append(String.format("- 총 PR: %d개\n", metrics.getTotalPRs()));
        sb.append(String.format("- 머지율: %.1f%% (%d merged / %d closed / %d open)\n",
                metrics.getMergeRate(), metrics.getMergedPRs(),
                metrics.getClosedWithoutMergePRs(), metrics.getOpenPRs()));
        sb.append(String.format("- 평균 리뷰 시간: %.1f시간\n", metrics.getAverageReviewTimeHours()));

        return sb.toString();
    }

    @Tool(description = "조직(Organization)의 전체 레포지토리 기여도를 분석합니다. 팀원별 기여도, 레포별 통계를 반환합니다.")
    public String getOrgStats(
            @ToolParam(description = "GitHub 액세스 토큰") String token,
            @ToolParam(description = "조직 이름 (예: Tally-lab)") String orgName,
            @ToolParam(description = "분석 대상 사용자명") String username) {
        log.info("Tool: get_org_stats {} for {}", orgName, username);

        OrganizationStats stats = contributionService.analyzeOrganization(token, orgName, username);

        StringBuilder sb = new StringBuilder();
        sb.append(String.format("## %s 조직 분석 — %s\n\n", orgName, username));
        sb.append(String.format("- 레포지토리: %d개\n", stats.getTotalRepositories()));
        sb.append(String.format("- 총 커밋: %d개 / 내 커밋: %d개 (%.1f%%)\n",
                stats.getTotalCommits(), stats.getUserCommits(), stats.getContributionPercentage()));

        if (stats.getRepositories() != null && !stats.getRepositories().isEmpty()) {
            sb.append("\n### 레포별 기여도\n");
            stats.getRepositories().stream()
                    .sorted((a, b) -> Integer.compare(b.getUserCommits(), a.getUserCommits()))
                    .forEach(r -> sb.append(String.format("- %s: %d/%d (%.1f%%)\n",
                            r.getName(), r.getUserCommits(), r.getTotalCommits(), r.getContributionPercentage())));
        }

        if (stats.getTeamMembers() != null && !stats.getTeamMembers().isEmpty()) {
            sb.append("\n### 팀원별 기여도 (상위 10명)\n");
            stats.getTeamMembers().stream().limit(10)
                    .forEach(m -> sb.append(String.format("- %s: %d커밋 (%.1f%%)\n",
                            m.getLogin(), m.getCommits(), m.getContributionPercentage())));
        }

        return sb.toString();
    }

    @Tool(description = "두 개 이상의 레포지토리를 비교 분석합니다. 각 레포의 기여도, 커밋 품질, PR 품질을 나란히 비교합니다.")
    public String compareRepos(
            @ToolParam(description = "GitHub 액세스 토큰") String token,
            @ToolParam(description = "비교할 레포 목록 (owner/repo 형식, 쉼표 구분)") String repoList,
            @ToolParam(description = "분석 대상 사용자명") String username) {
        log.info("Tool: compare_repos [{}] for {}", repoList, username);

        String[] repos = repoList.split(",");
        StringBuilder sb = new StringBuilder();
        sb.append(String.format("## 레포지토리 비교 분석 — %s\n\n", username));
        sb.append("| 항목 |");
        for (String r : repos) sb.append(String.format(" %s |", r.trim()));
        sb.append("\n|------|");
        for (String ignored : repos) sb.append("------|");
        sb.append("\n");

        // 각 레포 분석
        ContributionStats[] statsArr = new ContributionStats[repos.length];
        CommitQualityMetrics[] commitQArr = new CommitQualityMetrics[repos.length];
        PRQualityMetrics[] prQArr = new PRQualityMetrics[repos.length];

        for (int i = 0; i < repos.length; i++) {
            String[] parts = repos[i].trim().split("/");
            if (parts.length != 2) continue;

            GraphQLGitHubService.RepositoryData repoData = graphQLService.getRepositoryAnalysis(token, parts[0], parts[1]);
            statsArr[i] = contributionService.analyzeContribution(token, parts[0], parts[1], username);
            commitQArr[i] = qualityService.analyzeCommitQuality(repoData.getCommits());
            prQArr[i] = qualityService.analyzePRQuality(repoData.getPullRequests());
        }

        // 테이블 행 생성
        sb.append("| 기여도 |");
        for (ContributionStats s : statsArr) sb.append(s != null ? String.format(" %.1f%% |", s.getCommitPercentage()) : " N/A |");
        sb.append("\n| 커밋 수 |");
        for (ContributionStats s : statsArr) sb.append(s != null ? String.format(" %d/%d |", s.getUserCommits(), s.getTotalCommits()) : " N/A |");
        sb.append("\n| PR 수 |");
        for (ContributionStats s : statsArr) sb.append(s != null ? String.format(" %d |", s.getPullRequests() != null ? s.getPullRequests().size() : 0) : " N/A |");
        sb.append("\n| 커밋 품질 |");
        for (CommitQualityMetrics q : commitQArr) sb.append(q != null ? String.format(" %s (%.0f%%) |", q.getQualityGrade(), q.getConventionalCommitRate()) : " N/A |");
        sb.append("\n| PR 머지율 |");
        for (PRQualityMetrics q : prQArr) sb.append(q != null ? String.format(" %.1f%% |", q.getMergeRate()) : " N/A |");
        sb.append("\n");

        return sb.toString();
    }

    @Tool(description = "사용자의 조직 목록을 조회합니다.")
    public String listOrganizations(
            @ToolParam(description = "GitHub 액세스 토큰") String token) {
        log.info("Tool: list_organizations");
        List<Organization> orgs = graphQLService.getUserOrganizations(token);

        if (orgs.isEmpty()) return "소속된 조직이 없습니다.";

        return orgs.stream()
                .map(o -> String.format("- %s%s", o.getLogin(),
                        o.getDescription() != null ? " — " + o.getDescription() : ""))
                .collect(Collectors.joining("\n"));
    }
}
