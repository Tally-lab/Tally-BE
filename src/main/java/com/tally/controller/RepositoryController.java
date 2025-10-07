package com.tally.controller;

import com.tally.domain.GitHubRepository;
import com.tally.service.GitHubService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/repositories")
@RequiredArgsConstructor
public class RepositoryController {

    private final GitHubService gitHubService;

    @GetMapping
    public ResponseEntity<List<GitHubRepository>> getUserRepositories(
            @RequestHeader("Authorization") String authorization) {

        String accessToken = authorization.replace("Bearer ", "");
        log.info("Fetching repositories for user");

        List<GitHubRepository> repositories = gitHubService.getUserRepositories(accessToken);
        return ResponseEntity.ok(repositories);
    }

    @GetMapping("/{owner}/{repo}/commits")
    public ResponseEntity<Map<String, Object>> getRepositoryCommits(
            @PathVariable String owner,
            @PathVariable String repo,
            @RequestHeader("Authorization") String authorization) {

        String accessToken = authorization.replace("Bearer ", "");
        log.info("Fetching commits for {}/{}", owner, repo);

        Map<String, Object> commits = gitHubService.getRepositoryCommits(accessToken, owner, repo);
        return ResponseEntity.ok(commits);
    }
}