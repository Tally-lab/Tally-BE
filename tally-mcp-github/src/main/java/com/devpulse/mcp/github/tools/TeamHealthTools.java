package com.devpulse.mcp.github.tools;

import com.devpulse.mcp.github.service.TeamHealthService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

/**
 * MCP Server 도구 — 팀 건강 진단
 * Bus Factor, PR 리뷰 병목, 번아웃 위험, DORA 메트릭, 활동 요약, 스프린트 리포트
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TeamHealthTools {

    private final TeamHealthService teamHealthService;

    @Tool(description = "레포지토리의 Bus Factor를 진단합니다. 기여자 집중도를 분석하여 핵심 인력 이탈 위험을 평가합니다. Bus Factor 점수(1~5), 기여자별 커밋 비율, 코드 변경량을 반환합니다.")
    public String diagnoseBusFactor(
            @ToolParam(description = "GitHub 액세스 토큰") String token,
            @ToolParam(description = "레포지토리 소유자") String owner,
            @ToolParam(description = "레포지토리 이름") String repo) {
        log.info("Tool: diagnose_bus_factor {}/{}", owner, repo);
        try {
            return teamHealthService.diagnoseBusFactor(token, owner, repo);
        } catch (Exception e) {
            log.error("Tool: diagnose_bus_factor {}/{} failed", owner, repo, e);
            return String.format("Error: %s/%s Bus Factor 진단 실패 — %s", owner, repo, e.getMessage());
        }
    }

    @Tool(description = "PR 리뷰 병목을 진단합니다. 평균 첫 리뷰 시간, 머지까지 소요 시간, 현재 대기 중인 PR, 리뷰어별 응답 시간을 분석합니다. DORA 리드 타임 등급도 포함됩니다.")
    public String diagnoseReviewBottleneck(
            @ToolParam(description = "GitHub 액세스 토큰") String token,
            @ToolParam(description = "레포지토리 소유자") String owner,
            @ToolParam(description = "레포지토리 이름") String repo) {
        log.info("Tool: diagnose_review_bottleneck {}/{}", owner, repo);
        try {
            return teamHealthService.diagnoseReviewBottleneck(token, owner, repo);
        } catch (Exception e) {
            log.error("Tool: diagnose_review_bottleneck {}/{} failed", owner, repo, e);
            return String.format("Error: %s/%s 리뷰 병목 진단 실패 — %s", owner, repo, e.getMessage());
        }
    }

    @Tool(description = "팀원의 번아웃 위험을 감지합니다. 커밋 시간대 패턴(야간/주말 비율)을 분석하여 업무 강도 이상 징후를 식별합니다. 기여자별 위험도(정상/주의/높음)를 반환합니다.")
    public String diagnoseBurnoutRisk(
            @ToolParam(description = "GitHub 액세스 토큰") String token,
            @ToolParam(description = "레포지토리 소유자") String owner,
            @ToolParam(description = "레포지토리 이름") String repo) {
        log.info("Tool: diagnose_burnout_risk {}/{}", owner, repo);
        try {
            return teamHealthService.diagnoseBurnoutRisk(token, owner, repo);
        } catch (Exception e) {
            log.error("Tool: diagnose_burnout_risk {}/{} failed", owner, repo, e);
            return String.format("Error: %s/%s 번아웃 위험 분석 실패 — %s", owner, repo, e.getMessage());
        }
    }

    @Tool(description = "DORA 메트릭 4대 지표를 계산합니다. 배포 빈도, 리드 타임, 변경 실패율, MTTR을 측정하고 Elite/High/Medium/Low 등급을 부여합니다. 팀의 소프트웨어 개발 성숙도를 평가합니다.")
    public String calculateDoraMetrics(
            @ToolParam(description = "GitHub 액세스 토큰") String token,
            @ToolParam(description = "레포지토리 소유자") String owner,
            @ToolParam(description = "레포지토리 이름") String repo) {
        log.info("Tool: calculate_dora_metrics {}/{}", owner, repo);
        try {
            return teamHealthService.calculateDoraMetrics(token, owner, repo);
        } catch (Exception e) {
            log.error("Tool: calculate_dora_metrics {}/{} failed", owner, repo, e);
            return String.format("Error: %s/%s DORA 메트릭 계산 실패 — %s", owner, repo, e.getMessage());
        }
    }

    @Tool(description = "레포지토리의 최근 N일간 활동을 요약합니다. 커밋, PR, 이슈의 현황과 기여자별 통계를 반환합니다. 팀 현황 파악에 활용합니다.")
    public String getRecentActivity(
            @ToolParam(description = "GitHub 액세스 토큰") String token,
            @ToolParam(description = "레포지토리 소유자") String owner,
            @ToolParam(description = "레포지토리 이름") String repo,
            @ToolParam(description = "조회할 일수 (기본 7일)") int days) {
        log.info("Tool: get_recent_activity {}/{} days={}", owner, repo, days);
        try {
            return teamHealthService.getRecentActivity(token, owner, repo, days);
        } catch (Exception e) {
            log.error("Tool: get_recent_activity {}/{} failed", owner, repo, e);
            return String.format("Error: %s/%s 최근 활동 조회 실패 — %s", owner, repo, e.getMessage());
        }
    }

    @Tool(description = "스프린트 리포트를 자동 생성합니다. 최근 N일간의 완료 작업(feat/fix/refactor), 머지된 PR, 해결된 이슈, 진행 중인 작업, 팀 통계를 종합 요약합니다.")
    public String generateSprintReport(
            @ToolParam(description = "GitHub 액세스 토큰") String token,
            @ToolParam(description = "레포지토리 소유자") String owner,
            @ToolParam(description = "레포지토리 이름") String repo,
            @ToolParam(description = "스프린트 기간 일수 (기본 14일)") int days) {
        log.info("Tool: generate_sprint_report {}/{} days={}", owner, repo, days);
        try {
            return teamHealthService.generateSprintReport(token, owner, repo, days);
        } catch (Exception e) {
            log.error("Tool: generate_sprint_report {}/{} failed", owner, repo, e);
            return String.format("Error: %s/%s 스프린트 리포트 생성 실패 — %s", owner, repo, e.getMessage());
        }
    }
}
