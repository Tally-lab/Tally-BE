package com.tally.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tally.domain.User;
import com.tally.util.JsonFileUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.*;
import org.springframework.web.client.RestTemplate;

import java.util.*;

@Slf4j
public class AuthService {

    // 환경변수에서 읽거나 직접 설정 가능
    private String clientId;
    private String clientSecret;

    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();
    private static final String USERS_FILE = "data/users.json";

    public AuthService() {
        // 환경변수에서 읽기 (Lambda용)
        this.clientId = System.getenv("GITHUB_CLIENT_ID");
        this.clientSecret = System.getenv("GITHUB_CLIENT_SECRET");
    }

    public AuthService(String clientId, String clientSecret) {
        this.clientId = clientId;
        this.clientSecret = clientSecret;
    }

    /**
     * GitHub OAuth 코드로 액세스 토큰 받기
     */
    public String getAccessToken(String code) {
        String tokenUrl = "https://github.com/login/oauth/access_token";

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setAccept(Collections.singletonList(MediaType.APPLICATION_JSON));

        Map<String, String> requestBody = new HashMap<>();
        requestBody.put("client_id", clientId);
        requestBody.put("client_secret", clientSecret);
        requestBody.put("code", code);

        HttpEntity<Map<String, String>> request = new HttpEntity<>(requestBody, headers);

        try {
            ResponseEntity<String> response = restTemplate.exchange(
                    tokenUrl,
                    HttpMethod.POST,
                    request,
                    String.class
            );

            JsonNode jsonNode = objectMapper.readTree(response.getBody());
            String accessToken = jsonNode.get("access_token").asText();

            log.info("Successfully obtained access token");
            return accessToken;

        } catch (Exception e) {
            log.error("Failed to get access token", e);
            throw new RuntimeException("Failed to get access token: " + e.getMessage());
        }
    }

    /**
     * GitHub 사용자 정보 가져오기
     */
    public User getUserInfo(String accessToken) {
        String userUrl = "https://api.github.com/user";

        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "token " + accessToken);
        headers.setAccept(Collections.singletonList(MediaType.APPLICATION_JSON));

        HttpEntity<String> request = new HttpEntity<>(headers);

        try {
            ResponseEntity<String> response = restTemplate.exchange(
                    userUrl,
                    HttpMethod.GET,
                    request,
                    String.class
            );

            JsonNode jsonNode = objectMapper.readTree(response.getBody());

            User user = new User();
            user.setId(String.valueOf(jsonNode.get("id").asLong()));
            user.setUsername(jsonNode.get("login").asText());

            if (jsonNode.has("email") && !jsonNode.get("email").isNull()) {
                user.setEmail(jsonNode.get("email").asText());
            }

            if (jsonNode.has("avatar_url")) {
                user.setAvatarUrl(jsonNode.get("avatar_url").asText());
            }

            log.info("Successfully fetched user info for: {}", user.getUsername());
            return user;

        } catch (Exception e) {
            log.error("Failed to get user info", e);
            throw new RuntimeException("Failed to get user info: " + e.getMessage());
        }
    }

    /**
     * 사용자 정보 저장
     */
    public void saveUser(User user) {
        try {
            List<User> users = loadUsers();

            // 기존 사용자 찾기
            Optional<User> existingUser = users.stream()
                    .filter(u -> u.getId().equals(user.getId()))
                    .findFirst();

            if (existingUser.isPresent()) {
                // 기존 사용자 업데이트
                users.remove(existingUser.get());
                users.add(user);
                log.info("Updated existing user: {}", user.getUsername());
            } else {
                // 새 사용자 추가
                users.add(user);
                log.info("Added new user: {}", user.getUsername());
            }

            JsonFileUtil.saveToFile(USERS_FILE, users);

        } catch (Exception e) {
            log.error("Failed to save user", e);
            throw new RuntimeException("Failed to save user: " + e.getMessage());
        }
    }

    /**
     * 저장된 사용자 목록 불러오기
     */
    private List<User> loadUsers() {
        try {
            return JsonFileUtil.loadFromFile(USERS_FILE, User[].class);
        } catch (Exception e) {
            log.warn("No existing users file, creating new one");
            return new ArrayList<>();
        }
    }

    /**
     * 사용자 ID로 조회
     */
    public Optional<User> findUserById(String userId) {
        List<User> users = loadUsers();
        return users.stream()
                .filter(u -> u.getId().equals(userId))
                .findFirst();
    }

    /**
     * 사용자명으로 조회
     */
    public Optional<User> findUserByUsername(String username) {
        List<User> users = loadUsers();
        return users.stream()
                .filter(u -> u.getUsername().equals(username))
                .findFirst();
    }
}