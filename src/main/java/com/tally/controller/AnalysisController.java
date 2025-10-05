package com.tally.controller;

import com.tally.domain.AnalysisResult;
import com.tally.service.AnalysisService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/analysis")
@RequiredArgsConstructor
public class AnalysisController {

    private final AnalysisService analysisService;

    @PostMapping("/analyze")
    public ResponseEntity<AnalysisResult> analyzeData(@RequestBody Map<String, String> request) {
        String csvDataId = request.get("csvDataId");
        log.info("Analysis request for CSV data: {}", csvDataId);

        AnalysisResult result = analysisService.analyzeData(csvDataId);
        return ResponseEntity.ok(result);
    }

    @GetMapping("/result/{resultId}")
    public ResponseEntity<AnalysisResult> getAnalysisResult(@PathVariable String resultId) {
        AnalysisResult result = analysisService.getAnalysisResult(resultId);
        return ResponseEntity.ok(result);
    }

    @GetMapping("/result/csv/{csvDataId}")
    public ResponseEntity<AnalysisResult> getAnalysisResultByCSVDataId(@PathVariable String csvDataId) {
        AnalysisResult result = analysisService.getAnalysisResultByCSVDataId(csvDataId);
        return ResponseEntity.ok(result);
    }
}