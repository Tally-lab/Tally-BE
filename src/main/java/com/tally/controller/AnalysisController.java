package com.tally.controller;

import com.tally.domain.ContributionStats;
import com.tally.service.ContributionAnalysisService;
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
}