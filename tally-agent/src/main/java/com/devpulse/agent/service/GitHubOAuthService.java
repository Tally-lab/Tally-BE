package com.devpulse.agent.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.HashMap;
import java.util.Map;

@Slf4j
@Service
public class GitHubOAuthService {

    @Value("${github.oauth.client-id:}")
    private String clientId;

    @Value("${github.oauth.client-secret:}")
    private String clientSecret;

    @Value("${github.oauth.redirect-uri:http://localhost:5173/auth/callback}")
    private String redirectUri;

    private final WebClient webClient = WebClient.builder().build();
    private final ObjectMapper objectMapper = new ObjectMapper();

    public String getAuthorizationUrl() {
        return "https://github.com/login/oauth/authorize"
                + "?client_id=" + clientId
                + "&redirect_uri=" + redirectUri
                + "&scope=repo,read:org,read:user";
    }

    public Map<String, Object> exchangeCodeForUser(String code) {
        // Exchange code for access token
        String tokenResponse = webClient.post()
                .uri("https://github.com/login/oauth/access_token")
                .header(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
                .bodyValue(Map.of(
                        "client_id", clientId,
                        "client_secret", clientSecret,
                        "code", code,
                        "redirect_uri", redirectUri
                ))
                .retrieve()
                .bodyToMono(String.class)
                .block();

        try {
            JsonNode tokenNode = objectMapper.readTree(tokenResponse);

            if (tokenNode.has("error")) {
                throw new RuntimeException("GitHub OAuth error: " + tokenNode.get("error_description").asText());
            }

            String accessToken = tokenNode.get("access_token").asText();

            // Fetch user info
            String userResponse = webClient.get()
                    .uri("https://api.github.com/user")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                    .header(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();

            JsonNode userNode = objectMapper.readTree(userResponse);

            Map<String, Object> result = new HashMap<>();
            result.put("accessToken", accessToken);
            result.put("username", userNode.get("login").asText());
            result.put("avatarUrl", userNode.get("avatar_url").asText());
            result.put("id", String.valueOf(userNode.get("id").asLong()));

            log.info("GitHub OAuth login: {}", result.get("username"));
            return result;

        } catch (Exception e) {
            log.error("GitHub OAuth failed: {}", e.getMessage());
            throw new RuntimeException("GitHub OAuth failed: " + e.getMessage());
        }
    }
}
