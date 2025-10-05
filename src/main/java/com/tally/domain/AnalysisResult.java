package com.tally.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.Map;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AnalysisResult {
    private String id;
    private String userId;
    private String csvDataId;
    private Map<String, Object> hourlyStats;      // 시간대별 통계
    private Map<String, Object> categoryStats;    // 카테고리별 통계
    private String insights;                       // 인사이트
    private LocalDateTime analyzedAt;
}