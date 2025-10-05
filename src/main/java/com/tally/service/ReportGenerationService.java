package com.tally.service;

import com.tally.domain.ContributionStats;
import com.tally.domain.Report;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class ReportGenerationService {

    private final ContributionAnalysisService analysisService;

    public Report generateMarkdownReport(String statsId) {
        ContributionStats stats = analysisService.getStats(statsId);

        StringBuilder markdown = new StringBuilder();
        markdown.append("# 프로젝트 기여도 리포트\n\n");
        markdown.append("## 기본 정보\n");
        markdown.append(String.format("- **프로젝트**: %s\n", stats.getRepositoryFullName()));
        markdown.append(String.format("- **분석 일시**: %s\n\n",
                stats.getAnalyzedAt().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))));

        markdown.append("## 기여 통계\n");
        markdown.append(String.format("- **총 커밋 수**: %d개\n", stats.getTotalCommits()));
        markdown.append(String.format("- **내 커밋 수**: %d개\n", stats.getUserCommits()));
        markdown.append(String.format("- **기여율**: %.2f%%\n", stats.getCommitPercentage()));
        markdown.append(String.format("- **코드 추가**: +%d 라인\n", stats.getAdditions()));
        markdown.append(String.format("- **코드 삭제**: -%d 라인\n\n", stats.getDeletions()));

        markdown.append("## 활동 패턴\n");
        markdown.append("### 시간대별 커밋 수\n");
        Map<Integer, Integer> hourly = stats.getHourlyActivity();
        if (hourly != null && !hourly.isEmpty()) {
            hourly.entrySet().stream()
                    .sorted(Map.Entry.comparingByKey())
                    .forEach(entry -> markdown.append(String.format("- %02d:00 - %d회\n", entry.getKey(), entry.getValue())));
        }

        String content = markdown.toString();

        Report report = Report.builder()
                .id(UUID.randomUUID().toString())
                .userId(stats.getUserId())
                .contributionStatsId(statsId)
                .format(Report.ReportFormat.MARKDOWN)
                .content(content)
                .generatedAt(LocalDateTime.now())
                .build();

        log.info("Markdown report generated for stats: {}", statsId);
        return report;
    }

    public Report generateHtmlReport(String statsId) {
        ContributionStats stats = analysisService.getStats(statsId);

        StringBuilder html = new StringBuilder();
        html.append("<!DOCTYPE html>\n");
        html.append("<html><head><meta charset='UTF-8'>\n");
        html.append("<title>기여도 리포트</title>\n");
        html.append("<style>body{font-family:Arial;margin:40px;}</style>\n");
        html.append("</head><body>\n");
        html.append("<h1>프로젝트 기여도 리포트</h1>\n");
        html.append(String.format("<p><strong>프로젝트:</strong> %s</p>\n", stats.getRepositoryFullName()));
        html.append(String.format("<p><strong>기여율:</strong> %.2f%%</p>\n", stats.getCommitPercentage()));
        html.append(String.format("<p><strong>내 커밋:</strong> %d / %d</p>\n", stats.getUserCommits(), stats.getTotalCommits()));
        html.append("</body></html>");

        String content = html.toString();

        Report report = Report.builder()
                .id(UUID.randomUUID().toString())
                .userId(stats.getUserId())
                .contributionStatsId(statsId)
                .format(Report.ReportFormat.HTML)
                .content(content)
                .generatedAt(LocalDateTime.now())
                .build();

        log.info("HTML report generated for stats: {}", statsId);
        return report;
    }
}