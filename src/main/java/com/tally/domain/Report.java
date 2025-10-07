package com.tally.domain;

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
    private ReportFormat format;          // MARKDOWN, HTML, PNG
    private String content;               // 리포트 내용 또는 파일 경로
    private LocalDateTime generatedAt;

    public enum ReportFormat {
        MARKDOWN,
        HTML,
        PNG
    }
}