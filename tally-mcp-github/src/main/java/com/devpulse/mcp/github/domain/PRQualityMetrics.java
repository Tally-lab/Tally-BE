package com.devpulse.mcp.github.domain;

import lombok.Data;

import java.util.Map;

@Data
public class PRQualityMetrics {
    private double averageReviewTimeHours;
    private double averagePRSize;
    private double reviewParticipationRate;
    private double mergeRate;
    private int totalPRs;
    private int mergedPRs;
    private int closedWithoutMergePRs;
    private int openPRs;
    private Map<String, Integer> prSizeDistribution;
    private int totalReviews;
    private double averageReviewsPerPR;
    private String qualityGrade;

    public enum PRSize {
        SMALL, MEDIUM, LARGE, VERY_LARGE
    }

    public enum PRState {
        OPEN, CLOSED, MERGED
    }
}
