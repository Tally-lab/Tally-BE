package com.tally.service;

import com.tally.domain.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * 코드 품질 분석 서비스
 * 커밋, PR, 코드 복잡도 등의 품질 메트릭 분석
 */
@Slf4j
@Service
public class QualityAnalysisService {

    // Conventional Commits 패턴
    private static final Pattern CONVENTIONAL_COMMIT_PATTERN = Pattern.compile(
            "^(feat|fix|docs|style|refactor|test|chore|perf|ci|build|revert)(\\(.+\\))?!?: .+",
            Pattern.CASE_INSENSITIVE
    );

    private static final Pattern COMMIT_TYPE_PATTERN = Pattern.compile(
            "^(feat|fix|docs|style|refactor|test|chore|perf|ci|build|revert)",
            Pattern.CASE_INSENSITIVE
    );

    /**
     * 커밋 품질 분석
     */
    public CommitQualityMetrics analyzeCommitQuality(List<Commit> commits) {
        if (commits == null || commits.isEmpty()) {
            log.warn("No commits to analyze");
            return createEmptyCommitMetrics();
        }

        log.info("Analyzing commit quality for {} commits", commits.size());

        CommitQualityMetrics metrics = new CommitQualityMetrics();
        metrics.setTotalCommits(commits.size());

        // 1. Conventional Commits 준수율 계산
        int conventionalCount = 0;
        Map<String, Integer> typeDistribution = new HashMap<>();
        Map<String, Integer> sizeDistribution = new HashMap<>();

        int totalSize = 0;
        int analyzedCommits = 0;

        for (Commit commit : commits) {
            String message = commit.getCommit() != null ? commit.getCommit().getMessage() : "";

            // Conventional Commits 체크
            if (isConventionalCommit(message)) {
                conventionalCount++;
                String type = extractCommitType(message);
                typeDistribution.merge(type, 1, Integer::sum);
            } else {
                typeDistribution.merge("OTHER", 1, Integer::sum);
            }

            // 커밋 크기 추정 (파일 정보가 있는 경우)
            if (commit.getFiles() != null && !commit.getFiles().isEmpty()) {
                int commitSize = commit.getFiles().stream()
                        .mapToInt(f -> f.getAdditions() + f.getDeletions())
                        .sum();

                totalSize += commitSize;
                analyzedCommits++;

                CommitQualityMetrics.CommitSize size = classifyCommitSize(commitSize);
                sizeDistribution.merge(size.name(), 1, Integer::sum);
            }
        }

        metrics.setConventionalCommits(conventionalCount);
        metrics.setConventionalCommitRate((conventionalCount * 100.0) / commits.size());
        metrics.setCommitTypeDistribution(typeDistribution);
        metrics.setCommitSizeDistribution(sizeDistribution);

        // 평균 커밋 크기
        if (analyzedCommits > 0) {
            metrics.setAverageCommitSize((double) totalSize / analyzedCommits);
        }

        // 커밋 빈도 (시간 범위가 있으면 계산)
        metrics.setCommitFrequency(calculateCommitFrequency(commits));

        // 품질 등급 계산
        metrics.setQualityGrade(calculateCommitQualityGrade(metrics));

        log.info("Commit quality analysis complete: {}% conventional commits, grade: {}",
                String.format("%.1f", metrics.getConventionalCommitRate()),
                metrics.getQualityGrade());

        return metrics;
    }

    /**
     * PR 품질 분석
     */
    public PRQualityMetrics analyzePRQuality(List<PullRequest> prs) {
        if (prs == null || prs.isEmpty()) {
            log.warn("No PRs to analyze");
            return createEmptyPRMetrics();
        }

        log.info("Analyzing PR quality for {} PRs", prs.size());

        PRQualityMetrics metrics = new PRQualityMetrics();
        metrics.setTotalPRs(prs.size());

        int mergedCount = 0;
        int closedCount = 0;
        int openCount = 0;
        int totalReviewTime = 0;
        int analyzedReviewTime = 0;
        int totalPRSize = 0;
        int analyzedPRSize = 0;
        Map<String, Integer> sizeDistribution = new HashMap<>();

        for (PullRequest pr : prs) {
            // 상태별 집계
            String state = pr.getState();
            if ("MERGED".equalsIgnoreCase(state) || pr.getMergedAt() != null) {
                mergedCount++;
            } else if ("CLOSED".equalsIgnoreCase(state)) {
                closedCount++;
            } else if ("OPEN".equalsIgnoreCase(state)) {
                openCount++;
            }

            // 리뷰 시간 계산 (merged PR만)
            if (pr.getMergedAt() != null && pr.getCreatedAt() != null) {
                Duration reviewDuration = Duration.between(pr.getCreatedAt(), pr.getMergedAt());
                long hours = reviewDuration.toHours();
                totalReviewTime += hours;
                analyzedReviewTime++;
            }

            // PR 크기 추정
            // GraphQL에서 additions, deletions 정보를 가져올 수 있으므로 확장 필요
            // 현재는 간단히 추정
            // TODO: PR 도메인에 additions/deletions 필드 추가
        }

        metrics.setMergedPRs(mergedCount);
        metrics.setClosedWithoutMergePRs(closedCount);
        metrics.setOpenPRs(openCount);
        metrics.setMergeRate((mergedCount * 100.0) / prs.size());

        if (analyzedReviewTime > 0) {
            metrics.setAverageReviewTimeHours((double) totalReviewTime / analyzedReviewTime);
        }

        metrics.setPrSizeDistribution(sizeDistribution);

        // 품질 등급 계산
        metrics.setQualityGrade(calculatePRQualityGrade(metrics));

        log.info("PR quality analysis complete: {}% merge rate, avg review time: {} hours, grade: {}",
                String.format("%.1f", metrics.getMergeRate()),
                String.format("%.1f", metrics.getAverageReviewTimeHours()),
                metrics.getQualityGrade());

        return metrics;
    }

    // === Private Helper Methods ===

    /**
     * Conventional Commits 형식 체크
     */
    private boolean isConventionalCommit(String message) {
        if (message == null || message.isEmpty()) {
            return false;
        }

        String firstLine = message.split("\n")[0].trim();
        Matcher matcher = CONVENTIONAL_COMMIT_PATTERN.matcher(firstLine);
        return matcher.matches();
    }

    /**
     * 커밋 타입 추출 (feat, fix, docs 등)
     */
    private String extractCommitType(String message) {
        if (message == null || message.isEmpty()) {
            return "OTHER";
        }

        String firstLine = message.split("\n")[0].trim();
        Matcher matcher = COMMIT_TYPE_PATTERN.matcher(firstLine);

        if (matcher.find()) {
            return matcher.group(1).toUpperCase();
        }

        return "OTHER";
    }

    /**
     * 커밋 크기 분류
     */
    private CommitQualityMetrics.CommitSize classifyCommitSize(int linesChanged) {
        if (linesChanged < 50) {
            return CommitQualityMetrics.CommitSize.SMALL;
        } else if (linesChanged < 200) {
            return CommitQualityMetrics.CommitSize.MEDIUM;
        } else if (linesChanged < 500) {
            return CommitQualityMetrics.CommitSize.LARGE;
        } else {
            return CommitQualityMetrics.CommitSize.VERY_LARGE;
        }
    }

    /**
     * PR 크기 분류
     */
    private PRQualityMetrics.PRSize classifyPRSize(int linesChanged) {
        if (linesChanged < 100) {
            return PRQualityMetrics.PRSize.SMALL;
        } else if (linesChanged < 500) {
            return PRQualityMetrics.PRSize.MEDIUM;
        } else if (linesChanged < 1000) {
            return PRQualityMetrics.PRSize.LARGE;
        } else {
            return PRQualityMetrics.PRSize.VERY_LARGE;
        }
    }

    /**
     * 커밋 빈도 계산 (일일 평균)
     */
    private double calculateCommitFrequency(List<Commit> commits) {
        // committedDate를 파싱해서 날짜 범위 계산
        // 간단히 총 커밋 수 / 기간(일) 로 계산
        // TODO: 실제 날짜 파싱 구현
        return 0.0;
    }

    /**
     * 커밋 품질 등급 계산 (A-F)
     */
    private String calculateCommitQualityGrade(CommitQualityMetrics metrics) {
        double conventionalRate = metrics.getConventionalCommitRate();
        double avgSize = metrics.getAverageCommitSize();

        // 등급 기준:
        // A: 80%+ conventional, avgSize < 200
        // B: 60%+ conventional, avgSize < 300
        // C: 40%+ conventional, avgSize < 500
        // D: 20%+ conventional
        // F: < 20% conventional

        if (conventionalRate >= 80 && avgSize < 200) {
            return "A";
        } else if (conventionalRate >= 60 && avgSize < 300) {
            return "B";
        } else if (conventionalRate >= 40 && avgSize < 500) {
            return "C";
        } else if (conventionalRate >= 20) {
            return "D";
        } else {
            return "F";
        }
    }

    /**
     * PR 품질 등급 계산 (A-F)
     */
    private String calculatePRQualityGrade(PRQualityMetrics metrics) {
        double mergeRate = metrics.getMergeRate();
        double reviewTime = metrics.getAverageReviewTimeHours();

        // 등급 기준:
        // A: 80%+ merge rate, review time < 24h
        // B: 70%+ merge rate, review time < 48h
        // C: 60%+ merge rate, review time < 72h
        // D: 50%+ merge rate
        // F: < 50% merge rate

        if (mergeRate >= 80 && reviewTime < 24) {
            return "A";
        } else if (mergeRate >= 70 && reviewTime < 48) {
            return "B";
        } else if (mergeRate >= 60 && reviewTime < 72) {
            return "C";
        } else if (mergeRate >= 50) {
            return "D";
        } else {
            return "F";
        }
    }

    /**
     * 빈 커밋 메트릭 생성
     */
    private CommitQualityMetrics createEmptyCommitMetrics() {
        CommitQualityMetrics metrics = new CommitQualityMetrics();
        metrics.setTotalCommits(0);
        metrics.setConventionalCommits(0);
        metrics.setConventionalCommitRate(0.0);
        metrics.setAverageCommitSize(0.0);
        metrics.setCommitFrequency(0.0);
        metrics.setCommitSizeDistribution(new HashMap<>());
        metrics.setCommitTypeDistribution(new HashMap<>());
        metrics.setQualityGrade("N/A");
        return metrics;
    }

    /**
     * 빈 PR 메트릭 생성
     */
    private PRQualityMetrics createEmptyPRMetrics() {
        PRQualityMetrics metrics = new PRQualityMetrics();
        metrics.setTotalPRs(0);
        metrics.setMergedPRs(0);
        metrics.setClosedWithoutMergePRs(0);
        metrics.setOpenPRs(0);
        metrics.setMergeRate(0.0);
        metrics.setAverageReviewTimeHours(0.0);
        metrics.setPrSizeDistribution(new HashMap<>());
        metrics.setQualityGrade("N/A");
        return metrics;
    }
}
