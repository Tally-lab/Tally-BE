package com.tally.controller;

import com.tally.domain.CSVData;
import com.tally.service.CSVProcessingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/data")
@RequiredArgsConstructor
public class DataController {

    private final CSVProcessingService csvProcessingService;

    @PostMapping("/upload")
    public ResponseEntity<Map<String, Object>> uploadCSV(
            @RequestParam("file") MultipartFile file,
            @RequestParam("userId") String userId) {

        log.info("CSV upload request from user: {}", userId);

        try {
            CSVData csvData = csvProcessingService.processCSV(userId, file);
            List<String> columnTypes = csvProcessingService.detectColumnTypes(csvData);

            Map<String, Object> response = new HashMap<>();
            response.put("csvDataId", csvData.getId());
            response.put("fileName", csvData.getFileName());
            response.put("totalRows", csvData.getTotalRows());
            response.put("headers", csvData.getHeaders());
            response.put("columnTypes", columnTypes);
            response.put("uploadedAt", csvData.getUploadedAt());

            return ResponseEntity.ok(response);
        } catch (IOException e) {
            log.error("Failed to process CSV file", e);
            return ResponseEntity.badRequest().body(Map.of("error", "Failed to process CSV file"));
        }
    }

    @GetMapping("/{csvDataId}")
    public ResponseEntity<CSVData> getCSVData(@PathVariable String csvDataId) {
        CSVData csvData = csvProcessingService.getCSVData(csvDataId);
        return ResponseEntity.ok(csvData);
    }
}