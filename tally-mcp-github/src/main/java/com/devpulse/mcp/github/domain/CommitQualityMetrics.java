package com.devpulse.mcp.github.domain;

import lombok.Data;

import java.util.Map;

@Data
public class CommitQualityMetrics {
    private double averageCommitSize;
    private double conventionalCommitRate;
    private double commitFrequency;
    private Map<String, Integer> commitSizeDistribution;
    private Map<String, Integer> commitTypeDistribution;
    private int totalCommits;
    private int conventionalCommits;
    private String qualityGrade;

    public enum CommitSize {
        SMALL, MEDIUM, LARGE, VERY_LARGE
    }

    public enum CommitType {
        FEAT, FIX, DOCS, STYLE, REFACTOR, TEST, CHORE, PERF, CI, BUILD, REVERT, OTHER
    }
}
