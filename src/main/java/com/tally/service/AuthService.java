package com.tally.service;

import com.tally.config.GitHubOAuthConfig;
import com.tally.domain.User;
import com.tally.dto.GitHubUserResponse;
import com.tally.dto.OAuthTokenResponse;
import com.tally.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

@Service
@RequiredArgsConstructor
public class AuthService {
    private final UserRepository userRepository;
    private final GitHubOAuthConfig oauthConfig;
    private final RestTemplate restTemplate = new RestTemplate();

    /**
     * 사용자 로그인 (Access Token으로)
     */
    public User login(String accessToken) {
        User user = new User();
        user.setAccessToken(accessToken);
        return userRepository.save(user);
    }

    /**
     * 사용자 조회
     */
    public User getUserByToken(String accessToken) {
        return userRepository.findByAccessToken(accessToken)
                .orElseThrow(() -> new RuntimeException("User not found"));
    }

    /**
     * GitHub OAuth 인증 URL 생성
     */
    public String getAuthorizationUrl() {
        return String.format("%s?client_id=%s&redirect_uri=%s&scope=repo,user",
                oauthConfig.getAuthUrl(),
                oauthConfig.getClientId(),
                oauthConfig.getRedirectUri());
    }

    /**
     * Authorization Code를 Access Token으로 교환하고 사용자 정보 저장
     */
    public User exchangeCodeForToken(String code) {
        // 1. Access Token 발급
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
        headers.set("Accept", "application/json");

        MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        params.add("client_id", oauthConfig.getClientId());
        params.add("client_secret", oauthConfig.getClientSecret());
        params.add("code", code);
        params.add("redirect_uri", oauthConfig.getRedirectUri());

        HttpEntity<MultiValueMap<String, String>> request = new HttpEntity<>(params, headers);

        ResponseEntity<OAuthTokenResponse> response = restTemplate.postForEntity(
                oauthConfig.getTokenUrl(),
                request,
                OAuthTokenResponse.class
        );

        if (response.getBody() == null) {
            throw new RuntimeException("Failed to exchange code for token");
        }

        String accessToken = response.getBody().getAccessToken();

        // 2. GitHub 사용자 정보 가져오기
        GitHubUserResponse githubUser = fetchGitHubUserInfo(accessToken);

        // 3. User 객체 생성 및 저장
        User user = new User();
        user.setAccessToken(accessToken);
        user.setUsername(githubUser.getLogin());
        user.setEmail(githubUser.getEmail());
        user.setAvatarUrl(githubUser.getAvatarUrl());

        return userRepository.save(user);
    }

    /**
     * GitHub 사용자 정보 가져오기
     */
    public GitHubUserResponse fetchGitHubUserInfo(String accessToken) {
        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "token " + accessToken);
        headers.set("Accept", "application/json");

        HttpEntity<String> request = new HttpEntity<>(headers);

        ResponseEntity<GitHubUserResponse> response = restTemplate.exchange(
                "https://api.github.com/user",
                HttpMethod.GET,
                request,
                GitHubUserResponse.class
        );

        return response.getBody();
    }
}