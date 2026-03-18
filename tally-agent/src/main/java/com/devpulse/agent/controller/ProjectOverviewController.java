package com.devpulse.agent.controller;

import com.devpulse.agent.dto.ProjectOverviewResponse;
import com.devpulse.agent.service.ProjectOverviewService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/overview")
@RequiredArgsConstructor
public class ProjectOverviewController {

    private final ProjectOverviewService overviewService;

    @GetMapping("/{owner}/{repo}")
    public ResponseEntity<ProjectOverviewResponse> getOverview(
            @PathVariable String owner,
            @PathVariable String repo,
            @RequestHeader("X-GitHub-Token") String githubToken) {
        ProjectOverviewResponse overview = overviewService.getOverview(githubToken, owner, repo);
        return ResponseEntity.ok(overview);
    }
}
