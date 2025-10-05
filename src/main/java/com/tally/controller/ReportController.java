package com.tally.controller;

import com.tally.domain.Report;
import com.tally.service.ReportGenerationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping("/reports")
@RequiredArgsConstructor
public class ReportController {

    private final ReportGenerationService reportService;

    @PostMapping("/markdown/{statsId}")
    public ResponseEntity<String> generateMarkdownReport(@PathVariable String statsId) {
        log.info("Generating Markdown report for stats: {}", statsId);

        Report report = reportService.generateMarkdownReport(statsId);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.TEXT_PLAIN);
        headers.setContentDispositionFormData("attachment", "contribution-report.md");

        return ResponseEntity.ok()
                .headers(headers)
                .body(report.getContent());
    }

    @PostMapping("/html/{statsId}")
    public ResponseEntity<String> generateHtmlReport(@PathVariable String statsId) {
        log.info("Generating HTML report for stats: {}", statsId);

        Report report = reportService.generateHtmlReport(statsId);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.TEXT_HTML);

        return ResponseEntity.ok()
                .headers(headers)
                .body(report.getContent());
    }
}