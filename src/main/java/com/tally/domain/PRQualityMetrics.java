package com.tally.domain;

import lombok.Data;

import java.util.Map;

/**
 * PR (Pull Request) 품질 메트릭
 */
@Data
public class PRQualityMetrics {
    private double averageReviewTimeHours;         // 평균 리뷰 시간 (시간)
    private double averagePRSize;                  // 평균 PR 크기 (additions + deletions)
    private double reviewParticipationRate;        // 리뷰어 참여율 (리뷰가 있는 PR 비율)
    private double mergeRate;                      // 머지 성공률 (merged / total)
    private int totalPRs;
    private int mergedPRs;
    private int closedWithoutMergePRs;
    private int openPRs;
    private Map<String, Integer> prSizeDistribution;  // small/medium/large/very_large 분포
    private int totalReviews;                      // 전체 리뷰 수
    private double averageReviewsPerPR;            // PR당 평균 리뷰 수
    private String qualityGrade;                   // A-F 등급

    /**
     * PR 크기 분류
     */
    public enum PRSize {
        SMALL,       // < 100 lines
        MEDIUM,      // 100-500 lines
        LARGE,       // 500-1000 lines
        VERY_LARGE   // > 1000 lines
    }

    /**
     * PR 상태
     */
    public enum PRState {
        OPEN,
        CLOSED,
        MERGED
    }
}
