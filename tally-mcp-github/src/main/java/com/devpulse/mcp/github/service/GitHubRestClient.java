package com.devpulse.mcp.github.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

@Slf4j
@Component
public class GitHubRestClient {

    private static final String GITHUB_API_BASE = "https://api.github.com";
    private final WebClient webClient;
    private final ObjectMapper objectMapper;

    public GitHubRestClient() {
        this.webClient = WebClient.builder()
                .baseUrl(GITHUB_API_BASE)
                .defaultHeader(HttpHeaders.ACCEPT, "application/vnd.github.v3+json")
                .codecs(configurer -> configurer.defaultCodecs().maxInMemorySize(10 * 1024 * 1024))
                .build();
        this.objectMapper = new ObjectMapper();
    }

    /**
     * PR에서 변경된 파일 목록 + diff 패치 조회
     */
    public JsonNode getPrFiles(String token, String owner, String repo, int prNumber) {
        log.debug("REST: GET /repos/{}/{}/pulls/{}/files", owner, repo, prNumber);
        return executeGet(token, String.format("/repos/%s/%s/pulls/%d/files?per_page=100", owner, repo, prNumber));
    }

    /**
     * 레포지토리 최근 커밋 목록 조회
     */
    public JsonNode getCommits(String token, String owner, String repo, int perPage) {
        log.debug("REST: GET /repos/{}/{}/commits (perPage={})", owner, repo, perPage);
        return executeGet(token, String.format("/repos/%s/%s/commits?per_page=%d", owner, repo, perPage));
    }

    /**
     * 특정 커밋 상세 조회 (변경된 파일 목록 포함)
     */
    public JsonNode getCommitDetail(String token, String owner, String repo, String sha) {
        log.debug("REST: GET /repos/{}/{}/commits/{}", owner, repo, sha);
        return executeGet(token, String.format("/repos/%s/%s/commits/%s", owner, repo, sha));
    }

    /**
     * 특정 파일 경로의 커밋 히스토리 조회
     */
    public JsonNode getCommitsForPath(String token, String owner, String repo, String path, int perPage) {
        log.debug("REST: GET /repos/{}/{}/commits?path={}", owner, repo, path);
        return executeGet(token, String.format("/repos/%s/%s/commits?path=%s&per_page=%d", owner, repo, path, perPage));
    }

    private JsonNode executeGet(String token, String uri) {
        try {
            String response = webClient.get()
                    .uri(uri)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();
            return objectMapper.readTree(response);
        } catch (Exception e) {
            log.error("REST API call failed: {}", uri, e);
            throw new RuntimeException("GitHub REST API call failed: " + uri, e);
        }
    }
}
