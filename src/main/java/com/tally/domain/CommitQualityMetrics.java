package com.tally.domain;

import lombok.Data;

import java.util.Map;

/**
 * 커밋 품질 메트릭
 */
@Data
public class CommitQualityMetrics {
    private double averageCommitSize;              // 평균 커밋 크기 (additions + deletions)
    private double conventionalCommitRate;         // Conventional Commits 준수율 (0-100%)
    private double commitFrequency;                // 일일 평균 커밋 수
    private Map<String, Integer> commitSizeDistribution;  // small/medium/large 분포
    private Map<String, Integer> commitTypeDistribution;  // feat/fix/docs 등 타입별 분포
    private int totalCommits;
    private int conventionalCommits;               // Conventional Commits 형식 준수 커밋 수
    private String qualityGrade;                   // A-F 등급

    /**
     * 커밋 크기 분류
     */
    public enum CommitSize {
        SMALL,      // < 50 lines
        MEDIUM,     // 50-200 lines
        LARGE,      // 200-500 lines
        VERY_LARGE  // > 500 lines
    }

    /**
     * Conventional Commit 타입
     */
    public enum CommitType {
        FEAT,       // 새로운 기능
        FIX,        // 버그 수정
        DOCS,       // 문서
        STYLE,      // 스타일 (코드 변경 없음)
        REFACTOR,   // 리팩토링
        TEST,       // 테스트
        CHORE,      // 빌드/설정
        PERF,       // 성능 개선
        CI,         // CI 설정
        BUILD,      // 빌드 시스템
        REVERT,     // 되돌리기
        OTHER       // 기타
    }
}
