package com.devpulse.mcp.monitor.tools;

import com.devpulse.mcp.monitor.service.ServiceMonitorService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

/**
 * MCP Server 도구 — 서비스 모니터링
 * Spring Boot Actuator 기반 헬스체크, 메트릭 조회, JVM/HTTP 모니터링
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ServiceMonitorTools {

    private final ServiceMonitorService serviceMonitorService;

    @Tool(description = "서비스의 헬스체크 상태를 조회합니다. Spring Boot Actuator /health 엔드포인트를 호출하여 서비스 상태와 컴포넌트별 상태를 반환합니다.")
    public String checkHealth(
            @ToolParam(description = "서비스 URL (예: http://localhost:8080)") String serviceUrl) {
        log.info("Tool: check_health url={}", serviceUrl);
        return serviceMonitorService.checkHealth(serviceUrl);
    }

    @Tool(description = "서비스의 메트릭을 조회합니다. metricName이 비어있으면 사용 가능한 메트릭 목록을 반환합니다. 특정 메트릭 이름을 지정하면 해당 메트릭의 측정값과 태그를 반환합니다.")
    public String getMetrics(
            @ToolParam(description = "서비스 URL (예: http://localhost:8080)") String serviceUrl,
            @ToolParam(description = "메트릭 이름 (예: jvm.memory.used). 비워두면 전체 목록 반환") String metricName) {
        log.info("Tool: get_metrics url={} metric={}", serviceUrl, metricName);
        return serviceMonitorService.getMetrics(serviceUrl, metricName);
    }

    @Tool(description = "서비스의 기본 정보를 조회합니다. Spring Boot Actuator /info 엔드포인트를 호출하여 빌드 정보, Git 정보 등을 반환합니다.")
    public String getServiceInfo(
            @ToolParam(description = "서비스 URL (예: http://localhost:8080)") String serviceUrl) {
        log.info("Tool: get_service_info url={}", serviceUrl);
        return serviceMonitorService.getServiceInfo(serviceUrl);
    }

    @Tool(description = "등록된 모든 서비스의 헬스체크를 한번에 수행합니다. 각 서비스의 상태와 응답 시간을 요약 테이블로 반환합니다.")
    public String checkAllServices() {
        log.info("Tool: check_all_services");
        return serviceMonitorService.checkAllServices();
    }

    @Tool(description = "서비스의 JVM 메트릭을 조회합니다. 힙/논힙 메모리 사용량, GC 통계, 스레드 수, CPU 사용률, 가동 시간을 반환합니다.")
    public String getJvmMetrics(
            @ToolParam(description = "서비스 URL (예: http://localhost:8080)") String serviceUrl) {
        log.info("Tool: get_jvm_metrics url={}", serviceUrl);
        return serviceMonitorService.getJvmMetrics(serviceUrl);
    }

    @Tool(description = "서비스의 HTTP 요청 메트릭을 조회합니다. 전체 요청 수, 응답 시간, 엔드포인트별 분류, HTTP 상태 코드 분포를 반환합니다.")
    public String getHttpMetrics(
            @ToolParam(description = "서비스 URL (예: http://localhost:8080)") String serviceUrl) {
        log.info("Tool: get_http_metrics url={}", serviceUrl);
        return serviceMonitorService.getHttpMetrics(serviceUrl);
    }
}
