package com.devpulse.mcp.github.service;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.regex.Pattern;

@Slf4j
@Service
@RequiredArgsConstructor
public class CodeInspectionService {

    private final GitHubRestClient restClient;
    private final GitHubGraphQLClient graphQLClient;

    private static final Pattern FIX_PATTERN = Pattern.compile(
            "^fix(\\(.+\\))?!?:", Pattern.CASE_INSENSITIVE);

    /**
     * PR diff 분석 — 변경된 파일 목록과 패치 내용
     */
    public String analyzePrDiff(String token, String owner, String repo, int prNumber) {
        JsonNode files = restClient.getPrFiles(token, owner, repo, prNumber);

        StringBuilder sb = new StringBuilder();
        sb.append(String.format("## %s/%s PR #%d 변경사항\n\n", owner, repo, prNumber));

        int totalAdditions = 0, totalDeletions = 0, fileCount = 0;
        StringBuilder fileDetails = new StringBuilder();

        for (JsonNode file : files) {
            fileCount++;
            String filename = file.get("filename").asText();
            String status = file.get("status").asText();
            int additions = file.get("additions").asInt();
            int deletions = file.get("deletions").asInt();
            totalAdditions += additions;
            totalDeletions += deletions;

            fileDetails.append(String.format("### %s (%s) +%d -%d\n", filename, status, additions, deletions));

            if (file.has("patch") && !file.get("patch").isNull()) {
                String patch = file.get("patch").asText();
                if (patch.length() > 2000) {
                    patch = patch.substring(0, 2000) + "\n... (truncated)";
                }
                fileDetails.append("```diff\n").append(patch).append("\n```\n\n");
            }
        }

        sb.append(String.format("**총 %d개 파일** | +%d -%d\n\n", fileCount, totalAdditions, totalDeletions));
        sb.append(fileDetails);

        return sb.toString();
    }

    /**
     * 버그 핫스팟 분석 — fix 커밋이 자주 발생하는 파일 식별
     */
    public String analyzeFileBugHistory(String token, String owner, String repo) {
        JsonNode commits = restClient.getCommits(token, owner, repo, 100);

        List<String> fixShas = new ArrayList<>();
        Map<String, String> shaToMessage = new HashMap<>();

        for (JsonNode commit : commits) {
            String message = commit.get("commit").get("message").asText();
            if (FIX_PATTERN.matcher(message).find()) {
                String sha = commit.get("sha").asText();
                fixShas.add(sha);
                shaToMessage.put(sha, message.split("\n")[0]);
            }
        }

        if (fixShas.isEmpty()) {
            return String.format("## %s/%s 버그 핫스팟\n\n최근 100개 커밋 중 fix 커밋이 없습니다.", owner, repo);
        }

        Map<String, Integer> fileFixCount = new LinkedHashMap<>();
        Map<String, List<String>> fileFixMessages = new LinkedHashMap<>();
        int analyzed = 0;

        for (String sha : fixShas) {
            if (analyzed >= 20) break;
            try {
                JsonNode detail = restClient.getCommitDetail(token, owner, repo, sha);
                JsonNode filesNode = detail.get("files");
                if (filesNode != null) {
                    String message = shaToMessage.get(sha);
                    for (JsonNode file : filesNode) {
                        String filename = file.get("filename").asText();
                        fileFixCount.merge(filename, 1, Integer::sum);
                        fileFixMessages.computeIfAbsent(filename, k -> new ArrayList<>()).add(message);
                    }
                }
                analyzed++;
            } catch (Exception e) {
                log.warn("Failed to get commit detail for {}", sha, e);
            }
        }

        List<Map.Entry<String, Integer>> sorted = fileFixCount.entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                .limit(15)
                .toList();

        StringBuilder sb = new StringBuilder();
        sb.append(String.format("## %s/%s 버그 핫스팟\n\n", owner, repo));
        sb.append(String.format("최근 100개 커밋 중 **fix 커밋 %d개** (상위 %d개 상세 분석)\n\n", fixShas.size(), analyzed));
        sb.append("| 순위 | 파일 | 수정 횟수 | 위험도 |\n");
        sb.append("|------|------|-----------|--------|\n");

        int rank = 1;
        for (Map.Entry<String, Integer> entry : sorted) {
            String risk = entry.getValue() >= 5 ? "높음" :
                          entry.getValue() >= 3 ? "중간" : "낮음";
            sb.append(String.format("| %d | `%s` | %d | %s |\n",
                    rank++, entry.getKey(), entry.getValue(), risk));
        }

        sb.append("\n### 상위 핫스팟 상세\n");
        sorted.stream().limit(3).forEach(entry -> {
            sb.append(String.format("\n**%s** (%d회 수정)\n", entry.getKey(), entry.getValue()));
            List<String> messages = fileFixMessages.get(entry.getKey());
            if (messages != null) {
                messages.stream().limit(5).forEach(m -> sb.append(String.format("- %s\n", m)));
            }
        });

        return sb.toString();
    }

    /**
     * 파일별 기여자 분석 — 특정 파일에 누가 얼마나 기여했는지
     */
    public String analyzeFileContributors(String token, String owner, String repo, String path) {
        JsonNode commits = restClient.getCommitsForPath(token, owner, repo, path, 100);

        Map<String, Integer> authorCommitCount = new LinkedHashMap<>();
        Map<String, String> authorLatestDate = new LinkedHashMap<>();
        int totalCommits = 0;

        for (JsonNode commit : commits) {
            totalCommits++;
            String authorLogin;
            if (commit.has("author") && !commit.get("author").isNull()) {
                authorLogin = commit.get("author").get("login").asText();
            } else {
                authorLogin = commit.get("commit").get("author").get("name").asText();
            }

            authorCommitCount.merge(authorLogin, 1, Integer::sum);
            String date = commit.get("commit").get("author").get("date").asText();
            authorLatestDate.putIfAbsent(authorLogin, date);
        }

        List<Map.Entry<String, Integer>> sorted = authorCommitCount.entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                .toList();

        StringBuilder sb = new StringBuilder();
        sb.append(String.format("## %s/%s 파일 기여자 분석\n", owner, repo));
        sb.append(String.format("**파일**: `%s`\n\n", path));
        sb.append(String.format("총 커밋: %d개 | 기여자: %d명\n\n", totalCommits, authorCommitCount.size()));

        sb.append("| 기여자 | 커밋 수 | 비율 | 최근 활동 |\n");
        sb.append("|--------|---------|------|----------|\n");

        final int total = totalCommits;
        for (Map.Entry<String, Integer> entry : sorted) {
            double pct = total > 0 ? (double) entry.getValue() / total * 100 : 0;
            String latestDate = authorLatestDate.getOrDefault(entry.getKey(), "N/A");
            if (latestDate.length() > 10) latestDate = latestDate.substring(0, 10);
            sb.append(String.format("| @%s | %d | %.1f%% | %s |\n",
                    entry.getKey(), entry.getValue(), pct, latestDate));
        }

        if (!sorted.isEmpty() && totalCommits > 0) {
            double topPct = (double) sorted.get(0).getValue() / totalCommits * 100;
            if (topPct > 70) {
                sb.append(String.format(
                        "\n**Bus Factor 경고**: @%s가 이 파일 커밋의 %.0f%%를 담당합니다. 지식 공유가 필요합니다.\n",
                        sorted.get(0).getKey(), topPct));
            }
        }

        return sb.toString();
    }

    /**
     * PR 리뷰 히스토리 분석 — 리뷰 패턴, 리뷰어별 통계, 리뷰 소요시간
     */
    public String analyzeReviewHistory(String token, String owner, String repo) {
        JsonNode data = graphQLClient.getRepositoryAnalysis(token, owner, repo);
        JsonNode repoNode = data.get("repository");

        if (repoNode == null) {
            throw new RuntimeException("Repository not found: " + owner + "/" + repo);
        }

        JsonNode prsNode = repoNode.get("pullRequests").get("nodes");

        Map<String, Integer> reviewerCounts = new LinkedHashMap<>();
        Map<String, Map<String, Integer>> reviewerStateMap = new LinkedHashMap<>();
        Map<String, Integer> authorPrCounts = new LinkedHashMap<>();
        int totalPRs = 0;
        int reviewedPRs = 0;
        int totalReviews = 0;
        long totalReviewHours = 0;
        int reviewTimeSamples = 0;

        for (JsonNode pr : prsNode) {
            totalPRs++;
            String prAuthor = "Unknown";
            if (pr.has("author") && !pr.get("author").isNull()) {
                prAuthor = pr.get("author").get("login").asText();
            }
            authorPrCounts.merge(prAuthor, 1, Integer::sum);

            JsonNode reviewsNode = pr.get("reviews");
            if (reviewsNode == null || !reviewsNode.has("nodes")) continue;
            JsonNode reviews = reviewsNode.get("nodes");

            boolean hasReview = false;
            for (JsonNode review : reviews) {
                totalReviews++;
                hasReview = true;

                String reviewer = "Unknown";
                if (review.has("author") && !review.get("author").isNull()) {
                    reviewer = review.get("author").get("login").asText();
                }

                String state = review.get("state").asText();
                reviewerCounts.merge(reviewer, 1, Integer::sum);
                reviewerStateMap
                        .computeIfAbsent(reviewer, k -> new LinkedHashMap<>())
                        .merge(state, 1, Integer::sum);
            }

            if (hasReview && reviews.size() > 0) {
                reviewedPRs++;
                try {
                    String prCreated = pr.get("createdAt").asText();
                    String firstReview = reviews.get(0).get("createdAt").asText();
                    long hours = ChronoUnit.HOURS.between(
                            ZonedDateTime.parse(prCreated),
                            ZonedDateTime.parse(firstReview));
                    if (hours >= 0) {
                        totalReviewHours += hours;
                        reviewTimeSamples++;
                    }
                } catch (Exception e) {
                    log.debug("Failed to parse review time", e);
                }
            }
        }

        double avgReviewHours = reviewTimeSamples > 0 ? (double) totalReviewHours / reviewTimeSamples : 0;
        double reviewCoverage = totalPRs > 0 ? (double) reviewedPRs / totalPRs * 100 : 0;

        StringBuilder sb = new StringBuilder();
        sb.append(String.format("## %s/%s PR 리뷰 히스토리\n\n", owner, repo));
        sb.append(String.format("- 총 PR: %d개\n", totalPRs));
        sb.append(String.format("- 리뷰된 PR: %d개 (리뷰 커버리지 %.1f%%)\n", reviewedPRs, reviewCoverage));
        sb.append(String.format("- 총 리뷰: %d건\n", totalReviews));
        sb.append(String.format("- 평균 첫 리뷰 시간: %.1f시간\n", avgReviewHours));

        // 리뷰 품질 등급
        String grade;
        if (reviewCoverage >= 90 && avgReviewHours < 12) grade = "A";
        else if (reviewCoverage >= 70 && avgReviewHours < 24) grade = "B";
        else if (reviewCoverage >= 50 && avgReviewHours < 48) grade = "C";
        else if (reviewCoverage >= 30) grade = "D";
        else grade = "F";
        sb.append(String.format("- 리뷰 품질 등급: **%s**\n", grade));

        // 리뷰어별 통계
        if (!reviewerCounts.isEmpty()) {
            List<Map.Entry<String, Integer>> sortedReviewers = reviewerCounts.entrySet().stream()
                    .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                    .toList();

            sb.append("\n### 리뷰어별 통계\n");
            sb.append("| 리뷰어 | 리뷰 수 | APPROVED | CHANGES_REQUESTED | COMMENTED |\n");
            sb.append("|--------|---------|----------|-------------------|-----------|\n");

            for (Map.Entry<String, Integer> entry : sortedReviewers) {
                Map<String, Integer> states = reviewerStateMap.getOrDefault(entry.getKey(), Map.of());
                sb.append(String.format("| @%s | %d | %d | %d | %d |\n",
                        entry.getKey(), entry.getValue(),
                        states.getOrDefault("APPROVED", 0),
                        states.getOrDefault("CHANGES_REQUESTED", 0),
                        states.getOrDefault("COMMENTED", 0)));
            }
        }

        // PR 작성자별 통계
        if (!authorPrCounts.isEmpty()) {
            sb.append("\n### PR 작성자별 통계\n");
            authorPrCounts.entrySet().stream()
                    .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                    .forEach(e -> sb.append(String.format("- @%s: %d개 PR\n", e.getKey(), e.getValue())));
        }

        return sb.toString();
    }
}
