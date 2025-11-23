package com.tally.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Getter
@Setter
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

    // 역할 분석
    private Map<String, RoleStats> roleDistribution;    // 역할별 통계

    private List<PullRequest> pullRequests;             // 사용자의 PR 목록
    private List<Issue> issues;                         // 사용자의 Issue 목록

    private LocalDateTime analyzedAt;

    @Getter
    @Setter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RoleStats {
        private String roleName;
        private int commitCount;
        private double percentage;
    }
}