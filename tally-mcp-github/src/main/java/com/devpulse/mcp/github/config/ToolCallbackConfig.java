package com.devpulse.mcp.github.config;

import com.devpulse.mcp.github.tools.CodeInspectionTools;
import com.devpulse.mcp.github.tools.RepoAnalysisTools;
import com.devpulse.mcp.github.tools.TeamHealthTools;
import com.devpulse.mcp.github.tools.TechStackTools;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ToolCallbackConfig {

    @Bean
    ToolCallbackProvider repoAnalysisToolCallbackProvider(RepoAnalysisTools tools) {
        return MethodToolCallbackProvider.builder().toolObjects(tools).build();
    }

    @Bean
    ToolCallbackProvider codeInspectionToolCallbackProvider(CodeInspectionTools tools) {
        return MethodToolCallbackProvider.builder().toolObjects(tools).build();
    }

    @Bean
    ToolCallbackProvider teamHealthToolCallbackProvider(TeamHealthTools tools) {
        return MethodToolCallbackProvider.builder().toolObjects(tools).build();
    }

    @Bean
    ToolCallbackProvider techStackToolCallbackProvider(TechStackTools tools) {
        return MethodToolCallbackProvider.builder().toolObjects(tools).build();
    }
}
