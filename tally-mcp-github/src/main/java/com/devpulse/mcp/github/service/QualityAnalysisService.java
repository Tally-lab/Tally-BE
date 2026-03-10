package com.devpulse.mcp.github.service;

import com.devpulse.mcp.github.domain.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Service
public class QualityAnalysisService {

    private static final Pattern CONVENTIONAL_COMMIT_PATTERN = Pattern.compile(
            "^(feat|fix|docs|style|refactor|test|chore|perf|ci|build|revert)(\\(.+\\))?!?: .+",
            Pattern.CASE_INSENSITIVE
    );

    private static final Pattern COMMIT_TYPE_PATTERN = Pattern.compile(
            "^(feat|fix|docs|style|refactor|test|chore|perf|ci|build|revert)",
            Pattern.CASE_INSENSITIVE
    );

    public CommitQualityMetrics analyzeCommitQuality(List<Commit> commits) {
        if (commits == null || commits.isEmpty()) {
            return createEmptyCommitMetrics();
        }

        log.info("Analyzing commit quality for {} commits", commits.size());

        CommitQualityMetrics metrics = new CommitQualityMetrics();
        metrics.setTotalCommits(commits.size());

        int conventionalCount = 0;
        Map<String, Integer> typeDistribution = new HashMap<>();
        Map<String, Integer> sizeDistribution = new HashMap<>();
        int totalSize = 0;
        int analyzedCommits = 0;

        for (Commit commit : commits) {
            String message = commit.getCommit() != null ? commit.getCommit().getMessage() : "";

            if (isConventionalCommit(message)) {
                conventionalCount++;
                String type = extractCommitType(message);
                typeDistribution.merge(type, 1, Integer::sum);
            } else {
                typeDistribution.merge("OTHER", 1, Integer::sum);
            }

            if (commit.getFiles() != null && !commit.getFiles().isEmpty()) {
                int commitSize = commit.getFiles().stream()
                        .mapToInt(f -> f.getAdditions() + f.getDeletions())
                        .sum();
                totalSize += commitSize;
                analyzedCommits++;
                sizeDistribution.merge(classifyCommitSize(commitSize).name(), 1, Integer::sum);
            }
        }

        metrics.setConventionalCommits(conventionalCount);
        metrics.setConventionalCommitRate((conventionalCount * 100.0) / commits.size());
        metrics.setCommitTypeDistribution(typeDistribution);
        metrics.setCommitSizeDistribution(sizeDistribution);

        if (analyzedCommits > 0) {
            metrics.setAverageCommitSize((double) totalSize / analyzedCommits);
        }

        metrics.setQualityGrade(calculateCommitQualityGrade(metrics));

        log.info("Commit quality: {}% conventional, grade: {}",
                String.format("%.1f", metrics.getConventionalCommitRate()), metrics.getQualityGrade());

        return metrics;
    }

    public PRQualityMetrics analyzePRQuality(List<PullRequest> prs) {
        if (prs == null || prs.isEmpty()) {
            return createEmptyPRMetrics();
        }

        log.info("Analyzing PR quality for {} PRs", prs.size());

        PRQualityMetrics metrics = new PRQualityMetrics();
        metrics.setTotalPRs(prs.size());

        int mergedCount = 0, closedCount = 0, openCount = 0;
        long totalReviewHours = 0;
        int analyzedReviewTime = 0;

        for (PullRequest pr : prs) {
            String state = pr.getState();
            if ("MERGED".equalsIgnoreCase(state) || pr.getMergedAt() != null) {
                mergedCount++;
            } else if ("CLOSED".equalsIgnoreCase(state)) {
                closedCount++;
            } else if ("OPEN".equalsIgnoreCase(state)) {
                openCount++;
            }

            if (pr.getMergedAt() != null && pr.getCreatedAt() != null) {
                long hours = java.time.Duration.between(pr.getCreatedAt(), pr.getMergedAt()).toHours();
                totalReviewHours += hours;
                analyzedReviewTime++;
            }
        }

        metrics.setMergedPRs(mergedCount);
        metrics.setClosedWithoutMergePRs(closedCount);
        metrics.setOpenPRs(openCount);
        metrics.setMergeRate((mergedCount * 100.0) / prs.size());

        if (analyzedReviewTime > 0) {
            metrics.setAverageReviewTimeHours((double) totalReviewHours / analyzedReviewTime);
        }

        metrics.setPrSizeDistribution(new HashMap<>());
        metrics.setQualityGrade(calculatePRQualityGrade(metrics));

        log.info("PR quality: {}% merge rate, avg review: {} hours, grade: {}",
                String.format("%.1f", metrics.getMergeRate()),
                String.format("%.1f", metrics.getAverageReviewTimeHours()),
                metrics.getQualityGrade());

        return metrics;
    }

    private boolean isConventionalCommit(String message) {
        if (message == null || message.isEmpty()) return false;
        return CONVENTIONAL_COMMIT_PATTERN.matcher(message.split("\n")[0].trim()).matches();
    }

    private String extractCommitType(String message) {
        if (message == null || message.isEmpty()) return "OTHER";
        Matcher matcher = COMMIT_TYPE_PATTERN.matcher(message.split("\n")[0].trim());
        return matcher.find() ? matcher.group(1).toUpperCase() : "OTHER";
    }

    private CommitQualityMetrics.CommitSize classifyCommitSize(int linesChanged) {
        if (linesChanged < 50) return CommitQualityMetrics.CommitSize.SMALL;
        if (linesChanged < 200) return CommitQualityMetrics.CommitSize.MEDIUM;
        if (linesChanged < 500) return CommitQualityMetrics.CommitSize.LARGE;
        return CommitQualityMetrics.CommitSize.VERY_LARGE;
    }

    private String calculateCommitQualityGrade(CommitQualityMetrics metrics) {
        double rate = metrics.getConventionalCommitRate();
        double size = metrics.getAverageCommitSize();
        if (rate >= 80 && size < 200) return "A";
        if (rate >= 60 && size < 300) return "B";
        if (rate >= 40 && size < 500) return "C";
        if (rate >= 20) return "D";
        return "F";
    }

    private String calculatePRQualityGrade(PRQualityMetrics metrics) {
        double mergeRate = metrics.getMergeRate();
        double reviewTime = metrics.getAverageReviewTimeHours();
        if (mergeRate >= 80 && reviewTime < 24) return "A";
        if (mergeRate >= 70 && reviewTime < 48) return "B";
        if (mergeRate >= 60 && reviewTime < 72) return "C";
        if (mergeRate >= 50) return "D";
        return "F";
    }

    private CommitQualityMetrics createEmptyCommitMetrics() {
        CommitQualityMetrics m = new CommitQualityMetrics();
        m.setTotalCommits(0);
        m.setConventionalCommits(0);
        m.setConventionalCommitRate(0.0);
        m.setAverageCommitSize(0.0);
        m.setCommitSizeDistribution(new HashMap<>());
        m.setCommitTypeDistribution(new HashMap<>());
        m.setQualityGrade("N/A");
        return m;
    }

    private PRQualityMetrics createEmptyPRMetrics() {
        PRQualityMetrics m = new PRQualityMetrics();
        m.setTotalPRs(0);
        m.setMergedPRs(0);
        m.setClosedWithoutMergePRs(0);
        m.setOpenPRs(0);
        m.setMergeRate(0.0);
        m.setAverageReviewTimeHours(0.0);
        m.setPrSizeDistribution(new HashMap<>());
        m.setQualityGrade("N/A");
        return m;
    }
}
