package com.tally.lambda;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.tally.domain.*;
import com.tally.service.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Tally API Lambda Handler
 * 단일 Lambda에서 모든 API 요청을 처리 (Proxy 방식)
 */
public class TallyLambdaHandler implements RequestHandler<APIGatewayProxyRequestEvent, APIGatewayProxyResponseEvent> {

    private final ObjectMapper objectMapper;
    private final GitHubService gitHubService;
    private final AuthService authService;
    private final ContributionAnalysisService analysisService;
    private final ReportGenerationService reportService;
    private final AIAnalysisService aiService;
    private final PDFReportService pdfService;

    public TallyLambdaHandler() {
        this.objectMapper = new ObjectMapper();
        this.objectMapper.registerModule(new JavaTimeModule());

        this.gitHubService = new GitHubService();
        this.authService = new AuthService();
        this.analysisService = new ContributionAnalysisService(gitHubService);
        this.reportService = new ReportGenerationService(analysisService);
        this.aiService = new AIAnalysisService();
        this.pdfService = new PDFReportService();
    }

    @Override
    public APIGatewayProxyResponseEvent handleRequest(APIGatewayProxyRequestEvent input, Context context) {
        String path = input.getPath();
        String method = input.getHttpMethod();

        context.getLogger().log("Request: " + method + " " + path);

        try {
            // CORS preflight
            if ("OPTIONS".equals(method)) {
                return buildCorsResponse(200, "");
            }

            // 토큰 추출
            String token = extractToken(input);

            // 라우팅
            return route(path, method, input, token, context);

        } catch (Exception e) {
            context.getLogger().log("Error: " + e.getMessage());
            return buildErrorResponse(500, "Internal Server Error: " + e.getMessage());
        }
    }

    private APIGatewayProxyResponseEvent route(String path, String method,
            APIGatewayProxyRequestEvent input, String token, Context context) throws Exception {

        // Health check
        if (path.equals("/") || path.equals("/health")) {
            return buildSuccessResponse(Map.of("status", "ok", "service", "Tally API"));
        }

        // Auth endpoints
        if (path.startsWith("/auth")) {
            return handleAuth(path, method, input);
        }

        // 이하 모든 요청은 토큰 필요
        if (token == null || token.isEmpty()) {
            return buildErrorResponse(401, "Unauthorized: Token required");
        }

        // Repository endpoints
        if (path.startsWith("/repositories")) {
            return handleRepositories(path, method, token, context);
        }

        // Organization endpoints
        if (path.startsWith("/organizations")) {
            return handleOrganizations(path, method, token, input, context);
        }

        // Analysis endpoints
        if (path.startsWith("/analysis")) {
            return handleAnalysis(path, method, token, input, context);
        }

        // Report endpoints
        if (path.startsWith("/reports")) {
            return handleReports(path, method, token, input, context);
        }

        // AI endpoints
        if (path.startsWith("/ai")) {
            return handleAI(path, method, token, input, context);
        }

        return buildErrorResponse(404, "Not Found: " + path);
    }

    // ===== Auth Handlers =====
    private APIGatewayProxyResponseEvent handleAuth(String path, String method,
            APIGatewayProxyRequestEvent input) throws Exception {

        if (path.equals("/auth/github") && "GET".equals(method)) {
            String clientId = System.getenv("GITHUB_CLIENT_ID");
            String redirectUri = System.getenv("GITHUB_REDIRECT_URI");
            String authUrl = String.format(
                "https://github.com/login/oauth/authorize?client_id=%s&redirect_uri=%s&scope=repo,user,read:org",
                clientId, redirectUri
            );
            return buildSuccessResponse(Map.of("authUrl", authUrl));
        }

        if (path.equals("/auth/callback") && "GET".equals(method)) {
            String code = input.getQueryStringParameters().get("code");
            String accessToken = authService.getAccessToken(code);
            User user = authService.getUserInfo(accessToken);

            // 프론트엔드로 리다이렉트
            String frontendUrl = System.getenv("FRONTEND_URL");
            String redirectUrl = String.format("%s/auth/callback?accessToken=%s&userId=%s&username=%s",
                frontendUrl, accessToken, user.getId(), user.getUsername());

            return buildRedirectResponse(redirectUrl);
        }

        if (path.equals("/auth/login") && "POST".equals(method)) {
            Map<String, String> body = objectMapper.readValue(input.getBody(), Map.class);
            String accessToken = body.get("accessToken");
            User user = authService.getUserInfo(accessToken);
            return buildSuccessResponse(Map.of(
                "userId", user.getId(),
                "username", user.getUsername(),
                "avatarUrl", user.getAvatarUrl() != null ? user.getAvatarUrl() : ""
            ));
        }

        return buildErrorResponse(404, "Auth endpoint not found");
    }

    // ===== Repository Handlers =====
    private APIGatewayProxyResponseEvent handleRepositories(String path, String method,
            String token, Context context) throws Exception {

        if (path.equals("/repositories") && "GET".equals(method)) {
            List<GitHubRepository> repos = gitHubService.getAllRepositories(token);
            return buildSuccessResponse(repos);
        }

        // /repositories/{owner}/{repo}/commits
        if (path.matches("/repositories/[^/]+/[^/]+/commits") && "GET".equals(method)) {
            String[] parts = path.split("/");
            String owner = parts[2];
            String repo = parts[3];
            List<Commit> commits = gitHubService.getRepositoryCommits(token, owner, repo);
            return buildSuccessResponse(commits);
        }

        return buildErrorResponse(404, "Repository endpoint not found");
    }

    // ===== Organization Handlers =====
    private APIGatewayProxyResponseEvent handleOrganizations(String path, String method,
            String token, APIGatewayProxyRequestEvent input, Context context) throws Exception {

        if (path.equals("/organizations") && "GET".equals(method)) {
            List<Organization> orgs = gitHubService.getUserOrganizations(token);
            // API가 빈 결과를 반환하면 레포지토리에서 추출
            if (orgs.isEmpty()) {
                orgs = gitHubService.getOrganizationsFromRepositories(token);
            }
            return buildSuccessResponse(orgs);
        }

        // /organizations/{orgName}/stats
        if (path.matches("/organizations/[^/]+/stats") && "GET".equals(method)) {
            String[] parts = path.split("/");
            String orgName = parts[2];
            String username = input.getQueryStringParameters() != null
                ? input.getQueryStringParameters().get("username")
                : null;

            List<GitHubRepository> repos = gitHubService.getUserRepositoriesInOrganization(token, orgName, username);

            int totalCommits = 0;
            int userCommits = 0;
            int totalPRs = 0;
            int totalIssues = 0;

            List<Map<String, Object>> repoContributions = new ArrayList<>();

            for (GitHubRepository repo : repos) {
                ContributionStats stats = analysisService.analyzeContribution(token, orgName, repo.getName(), username);
                totalCommits += stats.getTotalCommits();
                userCommits += stats.getUserCommits();
                totalPRs += stats.getPullRequests().size();
                totalIssues += stats.getIssues().size();

                // 레포지토리별 기여도 정보 추가
                double repoPercentage = stats.getTotalCommits() > 0
                    ? (double) stats.getUserCommits() / stats.getTotalCommits() * 100
                    : 0;

                Map<String, Object> repoContribution = new HashMap<>();
                repoContribution.put("name", repo.getName());
                repoContribution.put("fullName", repo.getFullName());
                repoContribution.put("url", repo.getUrl() != null ? repo.getUrl() : "");
                repoContribution.put("totalCommits", stats.getTotalCommits());
                repoContribution.put("userCommits", stats.getUserCommits());
                repoContribution.put("contributionPercentage", Math.round(repoPercentage * 10.0) / 10.0);
                repoContributions.add(repoContribution);
            }

            double percentage = totalCommits > 0 ? (double) userCommits / totalCommits * 100 : 0;

            // 기여도 순으로 정렬
            repoContributions.sort((a, b) -> Double.compare(
                (Double) b.get("contributionPercentage"),
                (Double) a.get("contributionPercentage")
            ));

            // 기여한 레포지토리 수 (userCommits > 0)
            long contributedCount = repoContributions.stream()
                .filter(r -> (Integer) r.get("userCommits") > 0)
                .count();

            Map<String, Object> response = new HashMap<>();
            response.put("organizationName", orgName);
            response.put("totalRepositories", (int) contributedCount);
            response.put("totalCommits", totalCommits);
            response.put("userCommits", userCommits);
            response.put("contributionPercentage", Math.round(percentage * 10.0) / 10.0);
            response.put("totalPullRequests", totalPRs);
            response.put("totalIssues", totalIssues);
            response.put("repositories", repoContributions);

            return buildSuccessResponse(response);
        }

        // /organizations/{orgName}/repositories
        if (path.matches("/organizations/[^/]+/repositories") && "GET".equals(method)) {
            String[] parts = path.split("/");
            String orgName = parts[2];
            List<GitHubRepository> repos = gitHubService.getOrganizationRepositories(token, orgName);
            return buildSuccessResponse(repos);
        }

        return buildErrorResponse(404, "Organization endpoint not found");
    }

    // ===== Analysis Handlers =====
    private APIGatewayProxyResponseEvent handleAnalysis(String path, String method,
            String token, APIGatewayProxyRequestEvent input, Context context) throws Exception {

        // POST /analysis/analyze
        if (path.equals("/analysis/analyze") && "POST".equals(method)) {
            Map<String, String> body = objectMapper.readValue(input.getBody(), Map.class);
            String owner = body.get("owner");
            String repo = body.get("repo");
            String username = body.get("username");

            ContributionStats stats = analysisService.analyzeContribution(token, owner, repo, username);
            return buildSuccessResponse(stats);
        }

        // GET /analysis/{owner}/{repo}
        if (path.matches("/analysis/[^/]+/[^/]+") && "GET".equals(method)) {
            String[] parts = path.split("/");
            String owner = parts[2];
            String repo = parts[3];
            String username = input.getQueryStringParameters().get("username");

            ContributionStats stats = analysisService.analyzeContribution(token, owner, repo, username);
            return buildSuccessResponse(stats);
        }

        return buildErrorResponse(404, "Analysis endpoint not found");
    }

    // ===== Report Handlers =====
    private APIGatewayProxyResponseEvent handleReports(String path, String method,
            String token, APIGatewayProxyRequestEvent input, Context context) throws Exception {

        if ("POST".equals(method)) {
            Map<String, String> body = objectMapper.readValue(input.getBody(), Map.class);
            String owner = body.get("owner");
            String repo = body.get("repo");
            String username = body.get("username");

            if (path.equals("/reports/markdown")) {
                Report report = reportService.generateMarkdownReport(token, owner, repo, username);
                return buildSuccessResponse(report);
            }

            if (path.equals("/reports/html")) {
                Report report = reportService.generateHtmlReport(token, owner, repo, username);
                return buildSuccessResponse(report);
            }

            if (path.equals("/reports/pdf")) {
                // 기존 분석 수행
                ContributionStats stats = analysisService.analyzeContribution(token, owner, repo, username);
                // PDF 생성 (Base64 인코딩)
                String pdfBase64 = pdfService.generateReport(stats);
                return buildSuccessResponse(Map.of(
                    "content", pdfBase64,
                    "filename", repo + "-contribution-report.pdf",
                    "contentType", "application/pdf",
                    "encoding", "base64"
                ));
            }
        }

        return buildErrorResponse(404, "Report endpoint not found");
    }

    // ===== AI Handlers =====
    private APIGatewayProxyResponseEvent handleAI(String path, String method,
            String token, APIGatewayProxyRequestEvent input, Context context) throws Exception {

        if (path.equals("/ai/analyze") && "POST".equals(method)) {
            Map<String, String> body = objectMapper.readValue(input.getBody(), Map.class);
            String owner = body.get("owner");
            String repo = body.get("repo");
            String username = body.get("username");

            // 기존 분석 수행
            ContributionStats stats = analysisService.analyzeContribution(token, owner, repo, username);

            // AI 요약 생성
            String aiSummary = aiService.generateSummary(stats);

            return buildSuccessResponse(Map.of(
                "stats", stats,
                "aiSummary", aiSummary
            ));
        }

        return buildErrorResponse(404, "AI endpoint not found");
    }

    // ===== Helper Methods =====
    private String extractToken(APIGatewayProxyRequestEvent input) {
        Map<String, String> headers = input.getHeaders();
        if (headers == null) return null;

        String auth = headers.get("Authorization");
        if (auth == null) {
            auth = headers.get("authorization");
        }

        if (auth != null && auth.startsWith("Bearer ")) {
            return auth.substring(7);
        }
        return auth;
    }

    private APIGatewayProxyResponseEvent buildSuccessResponse(Object body) throws Exception {
        return buildCorsResponse(200, objectMapper.writeValueAsString(body));
    }

    private APIGatewayProxyResponseEvent buildErrorResponse(int statusCode, String message) {
        try {
            String body = objectMapper.writeValueAsString(Map.of("error", message));
            return buildCorsResponse(statusCode, body);
        } catch (Exception e) {
            return buildCorsResponse(statusCode, "{\"error\":\"" + message + "\"}");
        }
    }

    private APIGatewayProxyResponseEvent buildRedirectResponse(String url) {
        APIGatewayProxyResponseEvent response = new APIGatewayProxyResponseEvent();
        response.setStatusCode(302);
        Map<String, String> headers = getCorsHeaders();
        headers.put("Location", url);
        response.setHeaders(headers);
        return response;
    }

    private APIGatewayProxyResponseEvent buildCorsResponse(int statusCode, String body) {
        APIGatewayProxyResponseEvent response = new APIGatewayProxyResponseEvent();
        response.setStatusCode(statusCode);
        response.setHeaders(getCorsHeaders());
        response.setBody(body);
        return response;
    }

    private Map<String, String> getCorsHeaders() {
        Map<String, String> headers = new HashMap<>();
        headers.put("Content-Type", "application/json");
        headers.put("Access-Control-Allow-Origin", "*");
        headers.put("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, OPTIONS");
        headers.put("Access-Control-Allow-Headers", "Content-Type, Authorization");
        return headers;
    }
}
