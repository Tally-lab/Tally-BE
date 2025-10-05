package com.tally.service;

import com.tally.domain.AnalysisResult;
import com.tally.domain.CSVData;
import com.tally.repository.AnalysisResultRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class AnalysisService {

    private final AnalysisResultRepository analysisResultRepository;
    private final CSVProcessingService csvProcessingService;

    public AnalysisResult analyzeData(String csvDataId) {
        log.info("Starting analysis for CSV data: {}", csvDataId);

        CSVData csvData = csvProcessingService.getCSVData(csvDataId);

        // 시간대별 통계
        Map<String, Object> hourlyStats = analyzeHourlyPattern(csvData);

        // 카테고리별 통계
        Map<String, Object> categoryStats = analyzeCategoryPattern(csvData);

        // 인사이트 생성
        String insights = generateInsights(hourlyStats, categoryStats);

        // 결과 저장
        AnalysisResult result = AnalysisResult.builder()
                .id(UUID.randomUUID().toString())
                .userId(csvData.getUserId())
                .csvDataId(csvDataId)
                .hourlyStats(hourlyStats)
                .categoryStats(categoryStats)
                .insights(insights)
                .analyzedAt(LocalDateTime.now())
                .build();

        analysisResultRepository.save(result);
        log.info("Analysis completed for CSV data: {}", csvDataId);

        return result;
    }

    private Map<String, Object> analyzeHourlyPattern(CSVData csvData) {
        Map<String, Object> hourlyStats = new HashMap<>();

        // 시간 컬럼 찾기
        Optional<String> timeColumn = csvData.getHeaders().stream()
                .filter(header -> header.toLowerCase().contains("time") ||
                        header.toLowerCase().contains("시간"))
                .findFirst();

        if (timeColumn.isEmpty()) {
            log.warn("No time column found in CSV");
            return hourlyStats;
        }

        // 시간대별 빈도 계산
        Map<Integer, Long> hourFrequency = csvData.getRows().stream()
                .map(row -> row.get(timeColumn.get()))
                .filter(Objects::nonNull)
                .map(this::extractHour)
                .filter(hour -> hour >= 0 && hour < 24)
                .collect(Collectors.groupingBy(hour -> hour, Collectors.counting()));

        hourlyStats.put("frequency", hourFrequency);

        // 피크 시간대 찾기
        Optional<Map.Entry<Integer, Long>> peakHour = hourFrequency.entrySet().stream()
                .max(Map.Entry.comparingByValue());

        peakHour.ifPresent(entry -> {
            hourlyStats.put("peakHour", entry.getKey());
            hourlyStats.put("peakCount", entry.getValue());
        });

        return hourlyStats;
    }

    private Map<String, Object> analyzeCategoryPattern(CSVData csvData) {
        Map<String, Object> categoryStats = new HashMap<>();

        // 카테고리 컬럼 찾기
        Optional<String> categoryColumn = csvData.getHeaders().stream()
                .filter(header -> header.toLowerCase().contains("category") ||
                        header.toLowerCase().contains("카테고리") ||
                        header.toLowerCase().contains("type") ||
                        header.toLowerCase().contains("종류"))
                .findFirst();

        if (categoryColumn.isEmpty()) {
            log.warn("No category column found in CSV");
            return categoryStats;
        }

        // 카테고리별 빈도 계산
        Map<String, Long> categoryFrequency = csvData.getRows().stream()
                .map(row -> row.get(categoryColumn.get()))
                .filter(Objects::nonNull)
                .collect(Collectors.groupingBy(category -> category, Collectors.counting()));

        categoryStats.put("frequency", categoryFrequency);
        categoryStats.put("totalCategories", categoryFrequency.size());

        // 가장 많은 카테고리
        Optional<Map.Entry<String, Long>> topCategory = categoryFrequency.entrySet().stream()
                .max(Map.Entry.comparingByValue());

        topCategory.ifPresent(entry -> {
            categoryStats.put("topCategory", entry.getKey());
            categoryStats.put("topCount", entry.getValue());
        });

        return categoryStats;
    }

    private String generateInsights(Map<String, Object> hourlyStats, Map<String, Object> categoryStats) {
        StringBuilder insights = new StringBuilder();

        // 시간대 인사이트
        if (hourlyStats.containsKey("peakHour")) {
            int peakHour = (Integer) hourlyStats.get("peakHour");
            insights.append(String.format("가장 활동이 많은 시간대는 %d시입니다. ", peakHour));
        }

        // 카테고리 인사이트
        if (categoryStats.containsKey("topCategory")) {
            String topCategory = (String) categoryStats.get("topCategory");
            Long topCount = (Long) categoryStats.get("topCount");
            insights.append(String.format("가장 많이 기록된 카테고리는 '%s'이며, 총 %d회 기록되었습니다. ", topCategory, topCount));
        }

        if (categoryStats.containsKey("totalCategories")) {
            int totalCategories = (Integer) categoryStats.get("totalCategories");
            insights.append(String.format("총 %d개의 서로 다른 카테고리가 있습니다.", totalCategories));
        }

        return insights.toString();
    }

    private int extractHour(String timeStr) {
        try {
            // HH:mm 또는 HH:mm:ss 형식에서 시간 추출
            String[] parts = timeStr.split(":");
            return Integer.parseInt(parts[0]);
        } catch (Exception e) {
            log.warn("Failed to extract hour from: {}", timeStr);
            return -1;
        }
    }

    public AnalysisResult getAnalysisResult(String resultId) {
        return analysisResultRepository.findById(resultId)
                .orElseThrow(() -> new RuntimeException("Analysis result not found: " + resultId));
    }

    public AnalysisResult getAnalysisResultByCSVDataId(String csvDataId) {
        return analysisResultRepository.findByCsvDataId(csvDataId)
                .orElseThrow(() -> new RuntimeException("Analysis result not found for CSV data: " + csvDataId));
    }
}