package com.tally.service;

import com.tally.domain.GitHubRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class GitHubService {

    private final RestTemplate restTemplate = new RestTemplate();
    private static final String GITHUB_API_BASE = "https://api.github.com";

    public List<GitHubRepository> getUserRepositories(String accessToken) {
        String url = GITHUB_API_BASE + "/user/repos?per_page=100";

        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "Bearer " + accessToken);
        headers.set("Accept", "application/vnd.github+json");

        HttpEntity<String> entity = new HttpEntity<>(headers);

        try {
            ResponseEntity<List> response = restTemplate.exchange(
                    url,
                    HttpMethod.GET,
                    entity,
                    List.class
            );

            List<GitHubRepository> repositories = new ArrayList<>();
            List<Map<String, Object>> repoList = response.getBody();

            if (repoList != null) {
                for (Map<String, Object> repo : repoList) {
                    GitHubRepository repository = GitHubRepository.builder()
                            .id(UUID.randomUUID().toString())
                            .name((String) repo.get("name"))
                            .fullName((String) repo.get("full_name"))
                            .description((String) repo.get("description"))
                            .url((String) repo.get("html_url"))
                            .owner(((Map<String, String>) repo.get("owner")).get("login"))
                            .isPrivate((Boolean) repo.get("private"))
                            .defaultBranch((String) repo.get("default_branch"))
                            .createdAt(LocalDateTime.now())
                            .updatedAt(LocalDateTime.now())
                            .build();
                    repositories.add(repository);
                }
            }

            log.info("Fetched {} repositories", repositories.size());
            return repositories;

        } catch (Exception e) {
            log.error("Failed to fetch repositories", e);
            throw new RuntimeException("Failed to fetch GitHub repositories", e);
        }
    }

    public Map<String, Object> getRepositoryCommits(String accessToken, String owner, String repo) {
        String url = GITHUB_API_BASE + "/repos/" + owner + "/" + repo + "/commits?per_page=100";

        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "Bearer " + accessToken);
        headers.set("Accept", "application/vnd.github+json");

        HttpEntity<String> entity = new HttpEntity<>(headers);

        try {
            ResponseEntity<List> response = restTemplate.exchange(
                    url,
                    HttpMethod.GET,
                    entity,
                    List.class
            );

            List<Map<String, Object>> commits = response.getBody();
            log.info("Fetched {} commits for {}/{}", commits != null ? commits.size() : 0, owner, repo);

            return Map.of("commits", commits != null ? commits : new ArrayList<>());

        } catch (Exception e) {
            log.error("Failed to fetch commits for {}/{}", owner, repo, e);
            throw new RuntimeException("Failed to fetch commits", e);
        }
    }
}