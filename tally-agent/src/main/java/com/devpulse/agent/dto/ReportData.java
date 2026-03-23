package com.devpulse.agent.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.JsonNode;

import java.time.LocalDateTime;
import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ReportData(
        String reportId,
        String owner,
        String repo,
        LocalDateTime generatedAt,
        int analysisPeriodDays,
        JsonNode dora,
        JsonNode busFactor,
        JsonNode burnout,
        JsonNode commitQuality,
        JsonNode reviewBottleneck,
        JsonNode roleDistribution,
        JsonNode techStack,
        JsonNode recentActivity,
        AiDiagnosis aiDiagnosis
) {

    public record AiDiagnosis(
            String doraInterpretation,
            String busFactorInterpretation,
            String burnoutInterpretation,
            String commitQualityInterpretation,
            String reviewBottleneckInterpretation,
            List<ActionItem> immediateActions,
            List<ActionItem> improvements,
            List<ActionItem> strengths
    ) {}

    public record ActionItem(
            String icon,
            String title,
            String description
    ) {}
}
