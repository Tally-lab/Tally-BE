package com.tally.controller;

import com.tally.domain.ContributionStats;
import com.tally.domain.CommitQualityMetrics;
import com.tally.domain.PRQualityMetrics;
import com.tally.service.ContributionAnalysisService;
import com.tally.service.QualityAnalysisService;
import com.tally.service.GraphQLGitHubService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/analysis")
@RequiredArgsConstructor
public class AnalysisController {

    private final ContributionAnalysisService analysisService;
    private final QualityAnalysisService qualityAnalysisService;
    private final GraphQLGitHubService graphQLGitHubService;

    /**
     * GET 방식: URL 파라미터로 분석
     */
    @GetMapping("/{owner}/{repo}")
    public ResponseEntity<ContributionStats> analyzeContributionByPath(
            @PathVariable String owner,
            @PathVariable String repo,
            @RequestParam(required = false) String username,
            @RequestHeader("Authorization") String authorization) {

        String accessToken = authorization.replace("Bearer ", "");

        log.info("Analyzing contribution for user {} in {}/{}", username, owner, repo);

        ContributionStats stats = analysisService.analyzeContribution(
                accessToken, owner, repo, username
        );

        return ResponseEntity.ok(stats);
    }

    /**
     * POST 방식: Body로 분석 (기존 방식 유지)
     */
    @PostMapping("/analyze")
    public ResponseEntity<ContributionStats> analyzeContribution(
            @RequestBody Map<String, String> request,
            @RequestHeader("Authorization") String authorization) {

        String owner = request.get("owner");
        String repo = request.get("repo");
        String username = request.get("username");
        String accessToken = authorization.replace("Bearer ", "");

        log.info("Analyzing contribution for user {} in {}/{}", username, owner, repo);

        ContributionStats stats = analysisService.analyzeContribution(
                accessToken, owner, repo, username
        );

        return ResponseEntity.ok(stats);
    }

    /**
     * 커밋 품질 분석 (GraphQL 1회 호출로 완료)
     */
    @GetMapping("/quality/commits/{owner}/{repo}")
    public ResponseEntity<CommitQualityMetrics> analyzeCommitQuality(
            @PathVariable String owner,
            @PathVariable String repo,
            @RequestHeader("Authorization") String authorization) {

        String accessToken = authorization.replace("Bearer ", "");

        log.info("Analyzing commit quality for {}/{} using GraphQL", owner, repo);

        // GraphQL로 레포지토리 데이터 한번에 가져오기 (REST API 5 calls → 1 call)
        var repoData = graphQLGitHubService.getRepositoryAnalysis(accessToken, owner, repo);
        var commits = repoData.getCommits();

        // 커밋 품질 분석
        CommitQualityMetrics metrics = qualityAnalysisService.analyzeCommitQuality(commits);

        return ResponseEntity.ok(metrics);
    }

    /**
     * PR 품질 분석 (GraphQL 1회 호출로 완료)
     */
    @GetMapping("/quality/prs/{owner}/{repo}")
    public ResponseEntity<PRQualityMetrics> analyzePRQuality(
            @PathVariable String owner,
            @PathVariable String repo,
            @RequestHeader("Authorization") String authorization) {

        String accessToken = authorization.replace("Bearer ", "");

        log.info("Analyzing PR quality for {}/{} using GraphQL", owner, repo);

        // GraphQL로 레포지토리 데이터 한번에 가져오기 (REST API 5 calls → 1 call)
        var repoData = graphQLGitHubService.getRepositoryAnalysis(accessToken, owner, repo);
        var prs = repoData.getPullRequests();

        // PR 품질 분석
        PRQualityMetrics metrics = qualityAnalysisService.analyzePRQuality(prs);

        return ResponseEntity.ok(metrics);
    }
}