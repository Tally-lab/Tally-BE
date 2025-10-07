package com.tally.controller;

import com.tally.domain.Report;
import com.tally.service.ReportGenerationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/reports")
@RequiredArgsConstructor
public class ReportController {

    private final ReportGenerationService reportService;

    @PostMapping("/markdown")
    public ResponseEntity<String> generateMarkdownReport(
            @RequestBody Map<String, String> request,
            @RequestHeader("Authorization") String authorization) {

        String owner = request.get("owner");
        String repo = request.get("repo");
        String username = request.get("username");
        String accessToken = authorization.replace("Bearer ", "");

        log.info("Generating Markdown report for {}/{} - User: {}", owner, repo, username);

        Report report = reportService.generateMarkdownReport(accessToken, owner, repo, username);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.TEXT_PLAIN);
        headers.setContentDispositionFormData("attachment", "contribution-report.md");

        return ResponseEntity.ok()
                .headers(headers)
                .body(report.getContent());
    }

    @PostMapping("/html")
    public ResponseEntity<String> generateHtmlReport(
            @RequestBody Map<String, String> request,
            @RequestHeader("Authorization") String authorization) {

        String owner = request.get("owner");
        String repo = request.get("repo");
        String username = request.get("username");
        String accessToken = authorization.replace("Bearer ", "");

        log.info("Generating HTML report for {}/{} - User: {}", owner, repo, username);

        Report report = reportService.generateHtmlReport(accessToken, owner, repo, username);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.TEXT_HTML);

        return ResponseEntity.ok()
                .headers(headers)
                .body(report.getContent());
    }
}