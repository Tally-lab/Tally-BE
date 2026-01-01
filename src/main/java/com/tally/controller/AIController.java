package com.tally.controller;

import com.tally.domain.ContributionStats;
import com.tally.service.AIAnalysisService;
import com.tally.service.ContributionAnalysisService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/ai")
public class AIController {

    private final ContributionAnalysisService analysisService;
    private final AIAnalysisService aiAnalysisService;

    public AIController(ContributionAnalysisService analysisService, AIAnalysisService aiAnalysisService) {
        this.analysisService = analysisService;
        this.aiAnalysisService = aiAnalysisService;
    }

    /**
     * AI 기여도 분석 요약 생성
     */
    @GetMapping("/analyze/{owner}/{repo}")
    public ResponseEntity<Map<String, String>> analyzeWithAI(
            @PathVariable String owner,
            @PathVariable String repo,
            @RequestParam(required = false) String username,
            @RequestHeader("Authorization") String authorization) {

        String accessToken = authorization.replace("Bearer ", "");

        log.info("AI Analysis requested for user {} in {}/{}", username, owner, repo);

        // 1. 기여도 통계 수집
        ContributionStats stats = analysisService.analyzeContribution(
                accessToken, owner, repo, username
        );

        // 2. AI 요약 생성
        String aiSummary = aiAnalysisService.generateSummary(stats);

        log.info("AI Analysis completed for {}/{}", owner, repo);

        return ResponseEntity.ok(Map.of("aiSummary", aiSummary));
    }
}
