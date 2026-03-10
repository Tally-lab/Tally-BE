package com.devpulse.mcp.github.tools;

import com.devpulse.mcp.github.service.CodeInspectionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

/**
 * MCP Server 도구 — 코드 검사
 * PR diff, 버그 핫스팟, 파일 기여자, 리뷰 히스토리 분석
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CodeInspectionTools {

    private final CodeInspectionService codeInspectionService;

    @Tool(description = "PR의 변경사항(diff)을 조회합니다. 변경된 파일 목록, 추가/삭제 라인 수, 패치 내용을 반환합니다. 코드 리뷰 시 사용합니다.")
    public String getPrDiff(
            @ToolParam(description = "GitHub 액세스 토큰") String token,
            @ToolParam(description = "레포지토리 소유자") String owner,
            @ToolParam(description = "레포지토리 이름") String repo,
            @ToolParam(description = "PR 번호") int prNumber) {
        log.info("Tool: get_pr_diff {}/{} #{}", owner, repo, prNumber);
        return codeInspectionService.analyzePrDiff(token, owner, repo, prNumber);
    }

    @Tool(description = "레포지토리의 버그 핫스팟을 분석합니다. fix 커밋이 자주 발생하는 파일을 위험도별로 분류합니다. 코드 품질 개선 우선순위 결정에 활용합니다.")
    public String getFileBugHistory(
            @ToolParam(description = "GitHub 액세스 토큰") String token,
            @ToolParam(description = "레포지토리 소유자") String owner,
            @ToolParam(description = "레포지토리 이름") String repo) {
        log.info("Tool: get_file_bug_history {}/{}", owner, repo);
        return codeInspectionService.analyzeFileBugHistory(token, owner, repo);
    }

    @Tool(description = "특정 파일의 기여자를 분석합니다. 누가 얼마나 기여했는지, Bus Factor 경고를 포함합니다. 코드 리뷰어 지정이나 지식 공유 필요성 판단에 활용합니다.")
    public String getFileContributors(
            @ToolParam(description = "GitHub 액세스 토큰") String token,
            @ToolParam(description = "레포지토리 소유자") String owner,
            @ToolParam(description = "레포지토리 이름") String repo,
            @ToolParam(description = "파일 경로 (예: src/main/java/com/example/Service.java)") String path) {
        log.info("Tool: get_file_contributors {}/{} path={}", owner, repo, path);
        return codeInspectionService.analyzeFileContributors(token, owner, repo, path);
    }

    @Tool(description = "레포지토리의 PR 리뷰 히스토리를 분석합니다. 리뷰 커버리지, 리뷰어별 통계, 평균 리뷰 시간, 리뷰 품질 등급(A-F)을 반환합니다.")
    public String getReviewHistory(
            @ToolParam(description = "GitHub 액세스 토큰") String token,
            @ToolParam(description = "레포지토리 소유자") String owner,
            @ToolParam(description = "레포지토리 이름") String repo) {
        log.info("Tool: get_review_history {}/{}", owner, repo);
        return codeInspectionService.analyzeReviewHistory(token, owner, repo);
    }
}
