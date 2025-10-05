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
public class ContributionStats {
    private String id;
    private String userId;
    private String repositoryFullName;

    // 커밋 통계
    private int totalCommits;
    private int userCommits;
    private double commitPercentage;

    // 코드 통계
    private int additions;
    private int deletions;
    private Map<String, Integer> languageDistribution;  // 언어별 라인 수

    // 활동 패턴
    private Map<Integer, Integer> hourlyActivity;       // 시간대별 커밋 수
    private Map<String, Integer> dailyActivity;         // 요일별 커밋 수

    private LocalDateTime analyzedAt;
}