package com.devpulse.mcp.github.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Report {
    private String id;
    private String userId;
    private String contributionStatsId;
    private ReportFormat format;
    private String content;
    private LocalDateTime generatedAt;

    public enum ReportFormat {
        MARKDOWN, HTML, PDF
    }
}
