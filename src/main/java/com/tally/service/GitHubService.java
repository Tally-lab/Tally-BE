package com.tally.service;

import com.tally.domain.Commit;
import com.tally.domain.GitHubRepository;
import com.tally.domain.Issue;
import com.tally.domain.PullRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@Slf4j
@Service
public class GitHubService {
    private final RestTemplate restTemplate = new RestTemplate();

    /**
     * 사용자의 레포지토리 목록 조회
     */
    public List<GitHubRepository> getUserRepositories(String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "token " + token);
        headers.set("Accept", "application/json");

        HttpEntity<String> request = new HttpEntity<>(headers);

        ResponseEntity<GitHubRepository[]> response = restTemplate.exchange(
                "https://api.github.com/user/repos?per_page=100",
                HttpMethod.GET,
                request,
                GitHubRepository[].class
        );

        if (response.getBody() != null) {
            return Arrays.asList(response.getBody());
        }

        return new ArrayList<>();
    }

    /**
     * 레포지토리의 커밋 목록 조회
     */
    public List<Commit> getRepositoryCommits(String token, String owner, String repo) {
        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "token " + token);
        headers.set("Accept", "application/json");

        HttpEntity<String> request = new HttpEntity<>(headers);

        String url = String.format("https://api.github.com/repos/%s/%s/commits?per_page=100", owner, repo);

        ResponseEntity<Commit[]> response = restTemplate.exchange(
                url,
                HttpMethod.GET,
                request,
                Commit[].class
        );

        if (response.getBody() != null) {
            return Arrays.asList(response.getBody());
        }

        return new ArrayList<>();
    }

    /**
     * 레포지토리의 Pull Request 목록 조회
     */
    public List<PullRequest> getRepositoryPullRequests(String token, String owner, String repo) {
        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "token " + token);
        headers.set("Accept", "application/json");

        HttpEntity<String> request = new HttpEntity<>(headers);

        String url = String.format("https://api.github.com/repos/%s/%s/pulls?state=all&per_page=100", owner, repo);

        ResponseEntity<PullRequest[]> response = restTemplate.exchange(
                url,
                HttpMethod.GET,
                request,
                PullRequest[].class
        );

        if (response.getBody() != null) {
            return Arrays.asList(response.getBody());
        }

        return new ArrayList<>();
    }

    /**
     * 레포지토리의 Issue 목록 조회
     */
    public List<Issue> getRepositoryIssues(String token, String owner, String repo) {
        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "token " + token);
        headers.set("Accept", "application/json");

        HttpEntity<String> request = new HttpEntity<>(headers);

        String url = String.format("https://api.github.com/repos/%s/%s/issues?state=all&per_page=100", owner, repo);

        ResponseEntity<Issue[]> response = restTemplate.exchange(
                url,
                HttpMethod.GET,
                request,
                Issue[].class
        );

        if (response.getBody() != null) {
            return Arrays.asList(response.getBody());
        }

        return new ArrayList<>();
    }
}