package com.devpulse.agent.service;

import com.devpulse.agent.dto.ReportData;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.*;

@Slf4j
@Service
public class ReportService {

    private final ToolCallback[] allCallbacks;
    private final ChatClient chatClient;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final Map<String, ReportData> reportCache = new ConcurrentHashMap<>();
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();

    public ReportService(ChatClient.Builder chatClientBuilder, List<ToolCallbackProvider> toolCallbackProviders) {
        this.allCallbacks = toolCallbackProviders.stream()
                .flatMap(provider -> Arrays.stream(provider.getToolCallbacks()))
                .toArray(ToolCallback[]::new);
        this.chatClient = chatClientBuilder.build();
        log.info("ReportService initialized with {} tool callbacks", allCallbacks.length);
    }

    public ReportData generateReport(String githubToken, String owner, String repo) {
        String reportId = UUID.randomUUID().toString();
        log.info("Generating report [{}] for {}/{}", reportId, owner, repo);

        String tokenJson = buildTokenJson(githubToken, owner, repo);

        // 1. MCP 도구 호출 — raw 텍스트 수집
        String rawDora = callToolRaw("calculateDoraMetrics", tokenJson);
        String rawBusFactor = callToolRaw("diagnoseBusFactor", tokenJson);
        String rawBurnout = callToolRaw("diagnoseBurnoutRisk", tokenJson);
        String rawCommitQuality = callToolRaw("getCommitQuality", tokenJson);
        String rawReviewBottleneck = callToolRaw("diagnoseReviewBottleneck", tokenJson);
        String rawRoleDistribution = callToolRaw("analyzeRepo",
                buildAnalyzeRepoJson(githubToken, owner, repo));
        String rawTechStack = callToolRaw("analyzeTechStack", tokenJson);
        String rawRecentActivity = callToolRaw("getRecentActivity",
                buildRecentActivityJson(githubToken, owner, repo, 30));

        // 2. GPT-4o로 차트 호환 JSON + AI 해석 한번에 생성
        String fullReportJson = transformToChartData(owner, repo,
                rawDora, rawBusFactor, rawBurnout, rawCommitQuality,
                rawReviewBottleneck, rawRoleDistribution, rawTechStack, rawRecentActivity);

        // 3. 파싱 및 ReportData 조립
        ReportData report = parseFullReport(reportId, owner, repo, fullReportJson);

        // 4. 캐시 저장 (30분 TTL)
        reportCache.put(reportId, report);
        scheduler.schedule(() -> reportCache.remove(reportId), 30, TimeUnit.MINUTES);

        log.info("Report [{}] generated successfully for {}/{}", reportId, owner, repo);
        return report;
    }

    public ReportData getReport(String reportId) {
        return reportCache.get(reportId);
    }

    // --- MCP 도구 호출 (raw 텍스트 반환) ---

    private String callToolRaw(String toolName, String inputJson) {
        try {
            ToolCallback tool = findTool(toolName);
            if (tool == null) {
                log.warn("Tool not found: {}", toolName);
                return "Error: Tool not found - " + toolName;
            }
            String result = tool.call(inputJson);
            log.debug("Tool {} returned {} chars", toolName, result.length());
            return result;
        } catch (Exception e) {
            log.error("Tool {} call failed: {}", toolName, e.getMessage());
            return "Error: " + e.getMessage();
        }
    }

    private ToolCallback findTool(String toolNameSuffix) {
        return Arrays.stream(allCallbacks)
                .filter(t -> t.getToolDefinition().name().endsWith(toolNameSuffix))
                .findFirst()
                .orElse(null);
    }

    // --- GPT-4o 변환: raw 텍스트 → 차트 호환 JSON ---

    private String transformToChartData(String owner, String repo,
                                         String rawDora, String rawBusFactor, String rawBurnout,
                                         String rawCommitQuality, String rawReviewBottleneck,
                                         String rawRoleDistribution, String rawTechStack,
                                         String rawRecentActivity) {
        String prompt = """
                다음은 %s/%s 레포지토리의 MCP 도구 분석 결과(raw 텍스트)입니다.
                이 데이터를 프론트엔드 차트 컴포넌트가 사용할 수 있는 구조화된 JSON으로 변환하고,
                각 섹션별 AI 해석과 종합 진단도 함께 생성해주세요.

                ## Raw 분석 데이터

                ### DORA 메트릭
                %s

                ### Bus Factor
                %s

                ### 번아웃 위험도
                %s

                ### 커밋 품질
                %s

                ### 리뷰 병목
                %s

                ### 역할 분포
                %s

                ### 기술 스택
                %s

                ### 최근 활동
                %s

                ## 변환 규칙

                반드시 아래 JSON 구조로만 응답하세요. 다른 텍스트는 포함하지 마세요.
                데이터가 없거나 에러인 섹션은 null로 설정하세요.
                모든 숫자는 실제 데이터에서 추출하세요. 추측하지 마세요.

                ```json
                {
                  "dora": {
                    "repo": "%s/%s",
                    "overall": "Elite|High|Medium|Low",
                    "metrics": [
                      {"name": "배포 빈도", "value": 0, "grade": "Elite|High|Medium|Low|N/A", "display": "표시값", "elite": "하루 여러 번"},
                      {"name": "리드 타임", "value": 0, "grade": "...", "display": "...", "elite": "< 1시간"},
                      {"name": "변경 실패율", "value": 0, "grade": "...", "display": "...", "elite": "< 5%%"},
                      {"name": "MTTR", "value": 0, "grade": "...", "display": "...", "elite": "< 1시간"}
                    ]
                  },
                  "busFactor": {
                    "repo": "%s/%s",
                    "busFactor": 0,
                    "contributors": [{"name": "@user", "commits": 0, "percentage": 0}]
                  },
                  "burnout": {
                    "repo": "%s/%s",
                    "contributors": [{"name": "@user", "risk": "높음|주의|정상", "nightPercent": 0, "weekendPercent": 0, "hourly": [0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0]}]
                  },
                  "commitQuality": {
                    "repo": "%s/%s",
                    "grade": "A|B|C|D|F",
                    "totalCommits": 0,
                    "conventionalRate": 0,
                    "types": [{"name": "feat", "count": 0}]
                  },
                  "reviewBottleneck": {
                    "repo": "%s/%s",
                    "avgReviewTime": "시간",
                    "avgMergeTime": "시간",
                    "pendingPRs": 0,
                    "reviewers": [{"name": "@user", "reviews": 0, "avgResponseHours": 0}]
                  },
                  "roleDistribution": {
                    "repo": "%s/%s",
                    "username": "",
                    "roles": [{"name": "feature|bugfix|refactoring|documentation|infrastructure", "commits": 0, "percentage": 0}]
                  },
                  "techStack": {
                    "repo": "%s/%s",
                    "totalCount": 0,
                    "categories": [{"category": "카테고리명", "items": [{"name": "기술명", "version": "버전", "docsUrl": "URL", "source": "소스"}]}]
                  },
                  "recentActivity": {
                    "commits": 0,
                    "prsOpened": 0,
                    "prsMerged": 0,
                    "issuesClosed": 0
                  },
                  "aiDiagnosis": {
                    "doraInterpretation": "2-3문장 한국어 해석",
                    "busFactorInterpretation": "2-3문장 한국어 해석",
                    "burnoutInterpretation": "2-3문장 한국어 해석",
                    "commitQualityInterpretation": "2-3문장 한국어 해석",
                    "reviewBottleneckInterpretation": "2-3문장 한국어 해석",
                    "immediateActions": [{"icon": "🔴", "title": "제목", "description": "설명"}],
                    "improvements": [{"icon": "⚠️", "title": "제목", "description": "설명"}],
                    "strengths": [{"icon": "✅", "title": "제목", "description": "설명"}]
                  }
                }
                ```
                """.formatted(
                owner, repo,
                rawDora, rawBusFactor, rawBurnout, rawCommitQuality,
                rawReviewBottleneck, rawRoleDistribution, rawTechStack, rawRecentActivity,
                owner, repo, owner, repo, owner, repo, owner, repo,
                owner, repo, owner, repo, owner, repo);

        try {
            String response = chatClient.prompt()
                    .user(prompt)
                    .call()
                    .content();
            log.debug("GPT-4o chart transformation response length: {}", response.length());
            return response;
        } catch (Exception e) {
            log.error("GPT-4o chart transformation failed: {}", e.getMessage());
            return null;
        }
    }

    // --- 파싱 ---

    private ReportData parseFullReport(String reportId, String owner, String repo, String fullJson) {
        try {
            String json = extractJson(fullJson);
            JsonNode root = objectMapper.readTree(json);

            return new ReportData(
                    reportId, owner, repo, LocalDateTime.now(), 30,
                    root.path("dora").isMissingNode() ? null : root.get("dora"),
                    root.path("busFactor").isMissingNode() ? null : root.get("busFactor"),
                    root.path("burnout").isMissingNode() ? null : root.get("burnout"),
                    root.path("commitQuality").isMissingNode() ? null : root.get("commitQuality"),
                    root.path("reviewBottleneck").isMissingNode() ? null : root.get("reviewBottleneck"),
                    root.path("roleDistribution").isMissingNode() ? null : root.get("roleDistribution"),
                    root.path("techStack").isMissingNode() ? null : root.get("techStack"),
                    root.path("recentActivity").isMissingNode() ? null : root.get("recentActivity"),
                    parseAiDiagnosis(root.path("aiDiagnosis"))
            );
        } catch (Exception e) {
            log.error("Failed to parse full report JSON: {}", e.getMessage());
            return new ReportData(
                    reportId, owner, repo, LocalDateTime.now(), 30,
                    null, null, null, null, null, null, null, null,
                    fallbackDiagnosis()
            );
        }
    }

    private String extractJson(String text) {
        if (text == null) return "{}";
        String json = text.trim();
        if (json.contains("```json")) {
            json = json.substring(json.indexOf("```json") + 7);
            json = json.substring(0, json.lastIndexOf("```"));
        } else if (json.contains("```")) {
            json = json.substring(json.indexOf("```") + 3);
            json = json.substring(0, json.lastIndexOf("```"));
        }
        return json.trim();
    }

    private ReportData.AiDiagnosis parseAiDiagnosis(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return fallbackDiagnosis();
        }
        return new ReportData.AiDiagnosis(
                node.path("doraInterpretation").asText(""),
                node.path("busFactorInterpretation").asText(""),
                node.path("burnoutInterpretation").asText(""),
                node.path("commitQualityInterpretation").asText(""),
                node.path("reviewBottleneckInterpretation").asText(""),
                parseActionItems(node.path("immediateActions")),
                parseActionItems(node.path("improvements")),
                parseActionItems(node.path("strengths"))
        );
    }

    private List<ReportData.ActionItem> parseActionItems(JsonNode array) {
        List<ReportData.ActionItem> items = new ArrayList<>();
        if (array != null && array.isArray()) {
            for (JsonNode item : array) {
                items.add(new ReportData.ActionItem(
                        item.path("icon").asText(""),
                        item.path("title").asText(""),
                        item.path("description").asText("")
                ));
            }
        }
        return items;
    }

    private ReportData.AiDiagnosis fallbackDiagnosis() {
        return new ReportData.AiDiagnosis(
                "분석 데이터를 기반으로 진단을 생성하지 못했습니다.",
                "", "", "", "",
                List.of(), List.of(), List.of()
        );
    }

    // --- JSON 빌더 ---

    private String buildTokenJson(String token, String owner, String repo) {
        return """
                {"token":"%s","owner":"%s","repo":"%s"}"""
                .formatted(escapeJson(token), escapeJson(owner), escapeJson(repo));
    }

    private String buildAnalyzeRepoJson(String token, String owner, String repo) {
        return """
                {"token":"%s","owner":"%s","repo":"%s","username":""}"""
                .formatted(escapeJson(token), escapeJson(owner), escapeJson(repo));
    }

    private String buildRecentActivityJson(String token, String owner, String repo, int days) {
        return """
                {"token":"%s","owner":"%s","repo":"%s","days":%d}"""
                .formatted(escapeJson(token), escapeJson(owner), escapeJson(repo), days);
    }

    private String escapeJson(String value) {
        if (value == null) return "";
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
