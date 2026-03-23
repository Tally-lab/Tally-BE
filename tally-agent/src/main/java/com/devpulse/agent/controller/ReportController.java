package com.devpulse.agent.controller;

import com.devpulse.agent.dto.ReportData;
import com.devpulse.agent.service.PdfGenerationService;
import com.devpulse.agent.service.ReportService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping("/api/report")
@RequiredArgsConstructor
public class ReportController {

    private final ReportService reportService;
    private final PdfGenerationService pdfGenerationService;

    /**
     * 리포트 데이터 수집 + AI 진단 생성
     * POST /api/report/{owner}/{repo}/generate
     */
    @PostMapping("/{owner}/{repo}/generate")
    public ResponseEntity<ReportData> generateReport(
            @PathVariable String owner,
            @PathVariable String repo,
            @RequestHeader("X-GitHub-Token") String githubToken) {
        log.info("Report generation requested for {}/{}", owner, repo);
        ReportData report = reportService.generateReport(githubToken, owner, repo);
        return ResponseEntity.ok(report);
    }

    /**
     * 캐시된 리포트 데이터 조회 (FE 리포트 페이지에서 사용)
     * GET /api/report/{reportId}/data
     */
    @GetMapping("/{reportId}/data")
    public ResponseEntity<ReportData> getReportData(@PathVariable String reportId) {
        ReportData report = reportService.getReport(reportId);
        if (report == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(report);
    }

    /**
     * PDF 다운로드
     * GET /api/report/{reportId}/pdf
     */
    @GetMapping("/{reportId}/pdf")
    public ResponseEntity<byte[]> downloadPdf(@PathVariable String reportId) {
        ReportData report = reportService.getReport(reportId);
        if (report == null) {
            return ResponseEntity.notFound().build();
        }

        byte[] pdf = pdfGenerationService.generatePdf(reportId);

        String filename = "DevPulse-Report-%s-%s.pdf".formatted(report.owner(), report.repo());
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .contentType(MediaType.APPLICATION_PDF)
                .body(pdf);
    }
}
