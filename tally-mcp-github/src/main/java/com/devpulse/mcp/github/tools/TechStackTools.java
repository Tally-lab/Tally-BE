package com.devpulse.mcp.github.tools;

import com.devpulse.mcp.github.service.TechStackService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

/**
 * MCP Server 도구 — 기술 스택 분석 & 공식 문서 가이드
 * 레포지토리의 의존성 파일을 분석하여 기술 스택, 버전, 공식 문서 URL을 제공합니다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TechStackTools {

    private final TechStackService techStackService;

    @Tool(description = "레포지토리의 기술 스택을 분석합니다. 의존성 파일(package.json, build.gradle, pom.xml, requirements.txt, go.mod, Cargo.toml, Dockerfile)을 파싱하여 사용 중인 기술, 버전, 공식 문서 URL을 반환합니다. 프로젝트가 어떤 기술을 사용하는지 파악하거나, 특정 기술의 문서를 찾을 때 사용합니다.")
    public String analyzeTechStack(
            @ToolParam(description = "GitHub 액세스 토큰") String token,
            @ToolParam(description = "레포지토리 소유자") String owner,
            @ToolParam(description = "레포지토리 이름") String repo) {
        log.info("Tool: analyze_tech_stack {}/{}", owner, repo);
        try {
            return techStackService.analyzeTechStack(token, owner, repo);
        } catch (Exception e) {
            log.error("Tool: analyze_tech_stack {}/{} failed", owner, repo, e);
            return String.format("Error: %s/%s 기술 스택 분석 실패 — %s", owner, repo, e.getMessage());
        }
    }
}
