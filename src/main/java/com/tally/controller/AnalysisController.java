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