package com.tally.service;

import com.tally.domain.ContributionStats;
import lombok.extern.slf4j.Slf4j;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.core.SdkBytes;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.bedrockruntime.BedrockRuntimeClient;
import software.amazon.awssdk.services.bedrockruntime.model.InvokeModelRequest;
import software.amazon.awssdk.services.bedrockruntime.model.InvokeModelResponse;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.util.Map;
import java.util.stream.Collectors;

/**
 * AI 분석 서비스 - AWS Bedrock Claude를 사용한 기여도 요약
 */
@Slf4j
public class AIAnalysisService {

    private final BedrockRuntimeClient bedrockClient;
    private final ObjectMapper objectMapper;
    private final String modelId;

    public AIAnalysisService() {
        // Bedrock은 us-east-1에서 가장 안정적
        String region = System.getenv("BEDROCK_REGION");
        if (region == null || region.isEmpty()) {
            region = "us-east-1";
        }

        this.bedrockClient = BedrockRuntimeClient.builder()
                .region(Region.of(region))
                .credentialsProvider(DefaultCredentialsProvider.create())
                .build();

        this.objectMapper = new ObjectMapper();

        // Claude 3 Haiku 사용 (on-demand 지원)
        this.modelId = "anthropic.claude-3-haiku-20240307-v1:0";
    }

    /**
     * 기여도 통계를 기반으로 AI 요약 생성
     */
    public String generateSummary(ContributionStats stats) {
        try {
            String prompt = buildPrompt(stats);
            return callClaude(prompt);
        } catch (Exception e) {
            log.error("AI 요약 생성 실패: {}", e.getMessage());
            return generateFallbackSummary(stats);
        }
    }

    /**
     * 프롬프트 생성 - 커밋 메시지, PR, Issue 내용을 포함한 상세 분석
     */
    private String buildPrompt(ContributionStats stats) {
        StringBuilder sb = new StringBuilder();

        sb.append("당신은 개발자의 GitHub 기여도를 분석하여 이력서/자기소개서에 활용할 수 있는 전문적인 요약을 작성하는 전문가입니다.\n\n");
        sb.append("아래 정보를 바탕으로 이 개발자가 **구체적으로 무엇을 개발했고, 어떤 기능을 구현했는지** 파악하여 ");
        sb.append("취업용 자기소개서나 포트폴리오에 바로 사용할 수 있는 형태로 요약해주세요.\n\n");

        sb.append("=== 프로젝트 정보 ===\n");
        sb.append(String.format("레포지토리: %s\n", stats.getRepositoryFullName()));
        sb.append(String.format("총 커밋: %d개 중 %d개 기여 (%.1f%%)\n\n",
            stats.getTotalCommits(), stats.getUserCommits(), stats.getCommitPercentage()));

        // 커밋 메시지 (가장 중요!)
        if (stats.getCommitMessages() != null && !stats.getCommitMessages().isEmpty()) {
            sb.append("=== 주요 커밋 내역 (무엇을 개발했는지 파악) ===\n");
            stats.getCommitMessages().forEach(msg -> {
                sb.append(String.format("- %s\n", msg));
            });
            sb.append("\n");
        }

        // PR 정보
        if (stats.getPullRequests() != null && !stats.getPullRequests().isEmpty()) {
            sb.append("=== Pull Requests ===\n");
            stats.getPullRequests().stream().limit(10).forEach(pr -> {
                sb.append(String.format("- [%s] %s\n", pr.getState().toUpperCase(), pr.getTitle()));
                if (pr.getBody() != null && !pr.getBody().isEmpty()) {
                    String body = pr.getBody().length() > 200 ? pr.getBody().substring(0, 200) + "..." : pr.getBody();
                    sb.append(String.format("  설명: %s\n", body.replace("\n", " ")));
                }
            });
            sb.append("\n");
        }

        // Issue 정보
        if (stats.getIssues() != null && !stats.getIssues().isEmpty()) {
            sb.append("=== Issues ===\n");
            stats.getIssues().stream().limit(10).forEach(issue -> {
                sb.append(String.format("- [%s] %s\n", issue.getState().toUpperCase(), issue.getTitle()));
                if (issue.getBody() != null && !issue.getBody().isEmpty()) {
                    String body = issue.getBody().length() > 200 ? issue.getBody().substring(0, 200) + "..." : issue.getBody();
                    sb.append(String.format("  설명: %s\n", body.replace("\n", " ")));
                }
            });
            sb.append("\n");
        }

        // 역할 분포
        if (stats.getRoleDistribution() != null && !stats.getRoleDistribution().isEmpty()) {
            sb.append("=== 역할 분포 ===\n");
            stats.getRoleDistribution().entrySet().stream()
                .sorted((a, b) -> Double.compare(b.getValue().getPercentage(), a.getValue().getPercentage()))
                .filter(e -> e.getValue().getPercentage() > 5) // 5% 이상만
                .forEach(entry -> {
                    sb.append(String.format("- %s: %.1f%%\n", entry.getKey(), entry.getValue().getPercentage()));
                });
            sb.append("\n");
        }

        sb.append("=== 요청사항 ===\n");
        sb.append("위 커밋 메시지와 PR/Issue 내용을 분석하여 다음 형식으로 작성해주세요:\n\n");
        sb.append("1. **핵심 기여 요약** (2-3문장): 이 개발자가 이 프로젝트에서 구체적으로 무엇을 개발/구현했는지\n");
        sb.append("2. **주요 구현 기능** (bullet points): 커밋 메시지에서 파악한 주요 기능들\n");
        sb.append("3. **자기소개서용 한 줄**: 취업 자기소개서에 바로 쓸 수 있는 임팩트 있는 한 문장\n\n");
        sb.append("한국어로 작성하고, 단순한 통계가 아닌 **실제 개발 내용**을 중심으로 작성해주세요.");

        return sb.toString();
    }

    /**
     * Bedrock Claude API 호출
     */
    private String callClaude(String prompt) throws Exception {
        // Claude 메시지 API 형식
        ObjectNode requestBody = objectMapper.createObjectNode();
        requestBody.put("anthropic_version", "bedrock-2023-05-31");
        requestBody.put("max_tokens", 1500); // 상세 분석을 위해 토큰 증가

        ObjectNode message = objectMapper.createObjectNode();
        message.put("role", "user");
        message.put("content", prompt);

        requestBody.putArray("messages").add(message);

        InvokeModelRequest request = InvokeModelRequest.builder()
                .modelId(modelId)
                .contentType("application/json")
                .accept("application/json")
                .body(SdkBytes.fromUtf8String(objectMapper.writeValueAsString(requestBody)))
                .build();

        InvokeModelResponse response = bedrockClient.invokeModel(request);

        JsonNode responseJson = objectMapper.readTree(response.body().asUtf8String());

        // Claude 응답에서 텍스트 추출
        JsonNode content = responseJson.get("content");
        if (content != null && content.isArray() && content.size() > 0) {
            return content.get(0).get("text").asText();
        }

        return "AI 요약을 생성할 수 없습니다.";
    }

    /**
     * AI 호출 실패 시 기본 요약 생성
     */
    private String generateFallbackSummary(ContributionStats stats) {
        StringBuilder sb = new StringBuilder();

        String repoName = stats.getRepositoryFullName().split("/")[1];

        sb.append(String.format("%s 프로젝트에서 총 %d개의 커밋 중 %d개(%.1f%%)를 기여했습니다. ",
            repoName, stats.getTotalCommits(), stats.getUserCommits(), stats.getCommitPercentage()));

        if (stats.getRoleDistribution() != null && !stats.getRoleDistribution().isEmpty()) {
            String topRole = stats.getRoleDistribution().entrySet().stream()
                .max((a, b) -> Double.compare(a.getValue().getPercentage(), b.getValue().getPercentage()))
                .map(Map.Entry::getKey)
                .orElse("개발");

            sb.append(String.format("주요 역할은 %s이며, ", topRole));
        }

        int prCount = stats.getPullRequests() != null ? stats.getPullRequests().size() : 0;
        int issueCount = stats.getIssues() != null ? stats.getIssues().size() : 0;

        if (prCount > 0 || issueCount > 0) {
            sb.append(String.format("%d개의 PR과 %d개의 Issue를 처리했습니다.", prCount, issueCount));
        }

        return sb.toString();
    }
}
