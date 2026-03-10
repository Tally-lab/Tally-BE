package com.devpulse.mcp.monitor.service;

import com.devpulse.mcp.monitor.config.MonitorConfig;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ServiceMonitorService {

    private final MonitorConfig monitorConfig;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final WebClient webClient = WebClient.builder()
            .codecs(configurer -> configurer.defaultCodecs().maxInMemorySize(5 * 1024 * 1024))
            .build();

    public String checkHealth(String serviceUrl) {
        try {
            String response = fetchActuator(serviceUrl, "/actuator/health");
            JsonNode health = objectMapper.readTree(response);

            StringBuilder sb = new StringBuilder();
            sb.append("## 서비스 헬스체크 — ").append(serviceUrl).append("\n\n");

            String status = health.path("status").asText("UNKNOWN");
            String statusIcon = "UP".equals(status) ? "✅" : "DOWN".equals(status) ? "❌" : "⚠️";
            sb.append("**상태**: ").append(statusIcon).append(" ").append(status).append("\n\n");

            // Component details
            JsonNode components = health.path("components");
            if (!components.isMissingNode()) {
                sb.append("### 컴포넌트\n");
                sb.append("| 컴포넌트 | 상태 | 상세 |\n");
                sb.append("|----------|------|------|\n");

                components.fields().forEachRemaining(entry -> {
                    String compName = entry.getKey();
                    JsonNode comp = entry.getValue();
                    String compStatus = comp.path("status").asText("UNKNOWN");
                    String icon = "UP".equals(compStatus) ? "✅" : "❌";

                    String details = "";
                    JsonNode detailNode = comp.path("details");
                    if (!detailNode.isMissingNode()) {
                        details = detailNode.toString();
                        if (details.length() > 100) details = details.substring(0, 100) + "...";
                    }

                    sb.append(String.format("| %s | %s %s | %s |\n", compName, icon, compStatus, details));
                });
            }

            return sb.toString();

        } catch (Exception e) {
            return formatError("헬스체크", serviceUrl, e);
        }
    }

    public String getMetrics(String serviceUrl, String metricName) {
        try {
            if (metricName == null || metricName.isBlank()) {
                // List all available metrics
                String response = fetchActuator(serviceUrl, "/actuator/metrics");
                JsonNode metricsNode = objectMapper.readTree(response);

                StringBuilder sb = new StringBuilder();
                sb.append("## 사용 가능한 메트릭 — ").append(serviceUrl).append("\n\n");

                JsonNode names = metricsNode.path("names");
                if (names.isArray()) {
                    // Group by prefix
                    Map<String, List<String>> grouped = new TreeMap<>();
                    for (JsonNode name : names) {
                        String n = name.asText();
                        String prefix = n.contains(".") ? n.substring(0, n.indexOf('.')) : n;
                        grouped.computeIfAbsent(prefix, k -> new ArrayList<>()).add(n);
                    }

                    for (Map.Entry<String, List<String>> group : grouped.entrySet()) {
                        sb.append("### ").append(group.getKey()).append("\n");
                        for (String metric : group.getValue()) {
                            sb.append("- `").append(metric).append("`\n");
                        }
                        sb.append("\n");
                    }
                }

                return sb.toString();
            }

            // Get specific metric
            String response = fetchActuator(serviceUrl, "/actuator/metrics/" + metricName);
            JsonNode metric = objectMapper.readTree(response);

            StringBuilder sb = new StringBuilder();
            sb.append("## 메트릭: ").append(metricName).append("\n\n");
            sb.append("- **설명**: ").append(metric.path("description").asText("-")).append("\n");
            sb.append("- **단위**: ").append(metric.path("baseUnit").asText("-")).append("\n\n");

            JsonNode measurements = metric.path("measurements");
            if (measurements.isArray()) {
                sb.append("### 측정값\n");
                sb.append("| 통계 | 값 |\n");
                sb.append("|------|----|\n");
                for (JsonNode m : measurements) {
                    sb.append(String.format("| %s | %.4f |\n",
                            m.path("statistic").asText(), m.path("value").asDouble()));
                }
            }

            JsonNode tags = metric.path("availableTags");
            if (tags.isArray() && !tags.isEmpty()) {
                sb.append("\n### 필터 태그\n");
                for (JsonNode tag : tags) {
                    sb.append("- **").append(tag.path("tag").asText()).append("**: ");
                    JsonNode values = tag.path("values");
                    if (values.isArray()) {
                        List<String> vals = new ArrayList<>();
                        values.forEach(v -> vals.add(v.asText()));
                        sb.append(String.join(", ", vals));
                    }
                    sb.append("\n");
                }
            }

            return sb.toString();

        } catch (Exception e) {
            return formatError("메트릭 조회", serviceUrl, e);
        }
    }

    public String getServiceInfo(String serviceUrl) {
        try {
            String response = fetchActuator(serviceUrl, "/actuator/info");
            JsonNode info = objectMapper.readTree(response);

            StringBuilder sb = new StringBuilder();
            sb.append("## 서비스 정보 — ").append(serviceUrl).append("\n\n");

            if (info.isEmpty()) {
                sb.append("> 등록된 정보가 없습니다. `management.info.*` 설정을 확인하세요.\n");
            } else {
                sb.append("```json\n");
                sb.append(objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(info));
                sb.append("\n```\n");
            }

            return sb.toString();

        } catch (Exception e) {
            return formatError("서비스 정보 조회", serviceUrl, e);
        }
    }

    public String checkAllServices() {
        List<MonitorConfig.ServiceEntry> services = monitorConfig.getServices();
        if (services.isEmpty()) {
            return "## 전체 서비스 헬스체크\n\n> 등록된 모니터링 대상 서비스가 없습니다.";
        }

        StringBuilder sb = new StringBuilder();
        sb.append("## 전체 서비스 헬스체크\n\n");
        sb.append("| 서비스 | URL | 상태 | 응답 시간 |\n");
        sb.append("|--------|-----|------|----------|\n");

        int upCount = 0;
        int totalCount = services.size();

        for (MonitorConfig.ServiceEntry service : services) {
            long start = System.currentTimeMillis();
            String status;
            String icon;

            try {
                String response = fetchActuator(service.getUrl(), "/actuator/health");
                JsonNode health = objectMapper.readTree(response);
                status = health.path("status").asText("UNKNOWN");
                icon = "UP".equals(status) ? "✅" : "❌";
                if ("UP".equals(status)) upCount++;
            } catch (Exception e) {
                status = "DOWN";
                icon = "❌";
            }

            long elapsed = System.currentTimeMillis() - start;
            sb.append(String.format("| %s | %s | %s %s | %dms |\n",
                    service.getName(), service.getUrl(), icon, status, elapsed));
        }

        sb.append("\n**결과**: ").append(upCount).append("/").append(totalCount).append(" 서비스 정상");

        if (upCount < totalCount) {
            sb.append("\n\n> ⚠️ ").append(totalCount - upCount).append("개 서비스가 비정상 상태입니다.");
        } else {
            sb.append("\n\n> ✅ 모든 서비스가 정상 동작 중입니다.");
        }

        return sb.toString();
    }

    public String getJvmMetrics(String serviceUrl) {
        try {
            StringBuilder sb = new StringBuilder();
            sb.append("## JVM 메트릭 — ").append(serviceUrl).append("\n\n");

            // Heap memory
            sb.append("### 힙 메모리\n");
            appendMetricValue(sb, serviceUrl, "jvm.memory.used", "tags=area:heap", "사용량");
            appendMetricValue(sb, serviceUrl, "jvm.memory.max", "tags=area:heap", "최대");
            appendMetricValue(sb, serviceUrl, "jvm.memory.committed", "tags=area:heap", "할당");

            // Non-heap memory
            sb.append("\n### 논힙 메모리\n");
            appendMetricValue(sb, serviceUrl, "jvm.memory.used", "tags=area:nonheap", "사용량");
            appendMetricValue(sb, serviceUrl, "jvm.memory.committed", "tags=area:nonheap", "할당");

            // GC
            sb.append("\n### 가비지 컬렉션\n");
            appendMetricValue(sb, serviceUrl, "jvm.gc.pause", null, "GC 일시정지");

            // Threads
            sb.append("\n### 스레드\n");
            appendMetricValue(sb, serviceUrl, "jvm.threads.live", null, "활성 스레드");
            appendMetricValue(sb, serviceUrl, "jvm.threads.peak", null, "최대 스레드");
            appendMetricValue(sb, serviceUrl, "jvm.threads.daemon", null, "데몬 스레드");

            // CPU
            sb.append("\n### CPU\n");
            appendMetricValue(sb, serviceUrl, "process.cpu.usage", null, "프로세스 CPU");
            appendMetricValue(sb, serviceUrl, "system.cpu.usage", null, "시스템 CPU");

            // Uptime
            sb.append("\n### 가동 시간\n");
            appendMetricValue(sb, serviceUrl, "process.uptime", null, "업타임");

            return sb.toString();

        } catch (Exception e) {
            return formatError("JVM 메트릭 조회", serviceUrl, e);
        }
    }

    public String getHttpMetrics(String serviceUrl) {
        try {
            StringBuilder sb = new StringBuilder();
            sb.append("## HTTP 메트릭 — ").append(serviceUrl).append("\n\n");

            // Server requests
            String response = fetchActuator(serviceUrl, "/actuator/metrics/http.server.requests");
            JsonNode metric = objectMapper.readTree(response);

            JsonNode measurements = metric.path("measurements");
            if (measurements.isArray()) {
                sb.append("### 전체 HTTP 요청 통계\n");
                sb.append("| 통계 | 값 |\n");
                sb.append("|------|----|\n");
                for (JsonNode m : measurements) {
                    String stat = m.path("statistic").asText();
                    double value = m.path("value").asDouble();
                    String formatted;
                    if ("TOTAL_TIME".equals(stat) || "MAX".equals(stat)) {
                        formatted = String.format("%.3f s", value);
                    } else {
                        formatted = String.format("%.0f", value);
                    }
                    sb.append(String.format("| %s | %s |\n", stat, formatted));
                }
            }

            // Tags breakdown
            JsonNode tags = metric.path("availableTags");
            if (tags.isArray()) {
                for (JsonNode tag : tags) {
                    if ("uri".equals(tag.path("tag").asText())) {
                        sb.append("\n### 엔드포인트별 분류\n");
                        JsonNode uris = tag.path("values");
                        if (uris.isArray()) {
                            for (JsonNode uri : uris) {
                                sb.append("- `").append(uri.asText()).append("`\n");
                            }
                        }
                    }
                    if ("status".equals(tag.path("tag").asText())) {
                        sb.append("\n### HTTP 상태 코드\n");
                        JsonNode statuses = tag.path("values");
                        if (statuses.isArray()) {
                            for (JsonNode s : statuses) {
                                String code = s.asText();
                                String icon = code.startsWith("2") ? "✅" : code.startsWith("4") ? "⚠️" : "❌";
                                sb.append("- ").append(icon).append(" ").append(code).append("\n");
                            }
                        }
                    }
                }
            }

            return sb.toString();

        } catch (Exception e) {
            return formatError("HTTP 메트릭 조회", serviceUrl, e);
        }
    }

    private void appendMetricValue(StringBuilder sb, String serviceUrl, String metricName, String tagFilter, String label) {
        try {
            String path = "/actuator/metrics/" + metricName;
            if (tagFilter != null) path += "?" + tagFilter;
            String response = fetchActuator(serviceUrl, path);
            JsonNode metric = objectMapper.readTree(response);

            JsonNode measurements = metric.path("measurements");
            if (measurements.isArray()) {
                for (JsonNode m : measurements) {
                    double value = m.path("value").asDouble();
                    String unit = metric.path("baseUnit").asText("");

                    String formatted;
                    if ("bytes".equals(unit)) {
                        formatted = formatBytes(value);
                    } else if ("seconds".equals(unit)) {
                        formatted = formatDuration(value);
                    } else if (value < 1.0 && value > 0) {
                        formatted = String.format("%.2f%%", value * 100);
                    } else {
                        formatted = String.format("%.0f", value);
                    }

                    sb.append("- **").append(label).append("**: ").append(formatted).append("\n");
                    break;
                }
            }
        } catch (Exception e) {
            sb.append("- **").append(label).append("**: 조회 실패\n");
        }
    }

    private String fetchActuator(String baseUrl, String path) {
        return webClient.get()
                .uri(baseUrl + path)
                .retrieve()
                .bodyToMono(String.class)
                .timeout(Duration.ofSeconds(5))
                .block();
    }

    private String formatBytes(double bytes) {
        if (bytes >= 1_073_741_824) return String.format("%.2f GB", bytes / 1_073_741_824);
        if (bytes >= 1_048_576) return String.format("%.2f MB", bytes / 1_048_576);
        if (bytes >= 1024) return String.format("%.2f KB", bytes / 1024);
        return String.format("%.0f B", bytes);
    }

    private String formatDuration(double seconds) {
        if (seconds >= 3600) return String.format("%.1f h", seconds / 3600);
        if (seconds >= 60) return String.format("%.1f m", seconds / 60);
        if (seconds >= 1) return String.format("%.2f s", seconds);
        return String.format("%.0f ms", seconds * 1000);
    }

    private String formatError(String operation, String serviceUrl, Exception e) {
        log.error("{} 실패 [{}]: {}", operation, serviceUrl, e.getMessage());
        return String.format("## ❌ %s 실패\n\n- **서비스**: %s\n- **오류**: %s\n\n> 서비스가 실행 중인지, Actuator 엔드포인트가 노출되어 있는지 확인해주세요.",
                operation, serviceUrl, e.getMessage());
    }
}
