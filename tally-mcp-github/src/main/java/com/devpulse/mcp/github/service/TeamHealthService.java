package com.devpulse.mcp.github.service;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class TeamHealthService {

    private final GitHubGraphQLClient graphQLClient;
    private final GitHubRestClient restClient;

    private static final Pattern FIX_PATTERN = Pattern.compile(
            "^(fix|hotfix|revert)(\\(.+\\))?!?:", Pattern.CASE_INSENSITIVE);

    /**
     * Bus Factor 진단 — 기여자 집중도 분석
     * Bus Factor = 전체 커밋의 50%를 커버하는 데 필요한 최소 인원 수
     */
    public String diagnoseBusFactor(String token, String owner, String repo) {
        JsonNode data = graphQLClient.getRepositoryAnalysis(token, owner, repo);
        JsonNode repoNode = data.get("repository");
        if (repoNode == null) {
            throw new RuntimeException("Repository not found: " + owner + "/" + repo);
        }

        JsonNode commitsNode = repoNode.get("defaultBranchRef").get("target").get("history").get("nodes");
        int totalCommitCount = repoNode.get("defaultBranchRef").get("target").get("history").get("totalCount").asInt();

        // 기여자별 커밋 수 집계
        Map<String, Integer> authorCommits = new LinkedHashMap<>();
        Map<String, Integer> authorAdditions = new LinkedHashMap<>();
        Map<String, Integer> authorDeletions = new LinkedHashMap<>();

        for (JsonNode commit : commitsNode) {
            String author = extractAuthorLogin(commit);
            authorCommits.merge(author, 1, Integer::sum);
            authorAdditions.merge(author, commit.get("additions").asInt(), Integer::sum);
            authorDeletions.merge(author, commit.get("deletions").asInt(), Integer::sum);
        }

        int analyzedCommits = 0;
        for (int v : authorCommits.values()) analyzedCommits += v;

        // 기여도 내림차순 정렬
        List<Map.Entry<String, Integer>> sorted = authorCommits.entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                .toList();

        // Bus Factor 계산: 50% 커버에 필요한 인원 수
        int busFactorScore = 0;
        int accumulated = 0;
        int halfTotal = analyzedCommits / 2;
        for (Map.Entry<String, Integer> entry : sorted) {
            accumulated += entry.getValue();
            busFactorScore++;
            if (accumulated >= halfTotal) break;
        }

        StringBuilder sb = new StringBuilder();
        sb.append(String.format("## %s/%s Bus Factor 진단\n\n", owner, repo));
        sb.append(String.format("**Bus Factor: %d** ", busFactorScore));

        if (busFactorScore == 1) sb.append("(위험 — 핵심 기여자 1명에 의존)\n");
        else if (busFactorScore == 2) sb.append("(주의 — 소수 인원에 집중)\n");
        else if (busFactorScore <= 3) sb.append("(보통)\n");
        else sb.append("(양호 — 기여도가 분산되어 있음)\n");

        sb.append(String.format("\n분석 대상: 최근 커밋 %d개 (전체 %d개) | 기여자 %d명\n\n",
                analyzedCommits, totalCommitCount, authorCommits.size()));

        sb.append("### 기여자별 분포\n");
        sb.append("| 기여자 | 커밋 수 | 비율 | 코드 변경량 (추가/삭제) |\n");
        sb.append("|--------|---------|------|------------------------|\n");

        for (Map.Entry<String, Integer> entry : sorted) {
            double pct = analyzedCommits > 0 ? (double) entry.getValue() / analyzedCommits * 100 : 0;
            int adds = authorAdditions.getOrDefault(entry.getKey(), 0);
            int dels = authorDeletions.getOrDefault(entry.getKey(), 0);
            sb.append(String.format("| @%s | %d | %.1f%% | +%d / -%d |\n",
                    entry.getKey(), entry.getValue(), pct, adds, dels));
        }

        // 위험 영역 식별
        if (!sorted.isEmpty()) {
            double topPct = (double) sorted.get(0).getValue() / analyzedCommits * 100;
            if (topPct > 70) {
                sb.append(String.format(
                        "\n### 위험 경고\n@%s가 전체 커밋의 %.0f%%를 담당하고 있습니다.\n" +
                        "이 기여자가 이탈할 경우 프로젝트 유지에 심각한 위험이 발생합니다.\n\n" +
                        "**권고 조치**:\n" +
                        "1. 페어 프로그래밍 또는 코드 리뷰 강화\n" +
                        "2. 핵심 모듈 문서화\n" +
                        "3. 다른 팀원에게 점진적으로 작업 이관\n",
                        sorted.get(0).getKey(), topPct));
            } else if (topPct > 50) {
                sb.append(String.format(
                        "\n### 주의\n@%s가 전체 커밋의 %.0f%%를 담당합니다. 지식 공유 강화를 권고합니다.\n",
                        sorted.get(0).getKey(), topPct));
            }
        }

        return sb.toString();
    }

    /**
     * PR 리뷰 병목 진단 — 리뷰 대기 시간, 병목 지점, 대기 중 PR 식별
     */
    public String diagnoseReviewBottleneck(String token, String owner, String repo) {
        JsonNode data = graphQLClient.getRepositoryAnalysis(token, owner, repo);
        JsonNode repoNode = data.get("repository");
        if (repoNode == null) {
            throw new RuntimeException("Repository not found: " + owner + "/" + repo);
        }

        JsonNode prsNode = repoNode.get("pullRequests").get("nodes");

        Map<String, List<Long>> reviewerResponseHours = new LinkedHashMap<>();
        List<Long> timeToFirstReviewHours = new ArrayList<>();
        List<Long> timeToMergeHours = new ArrayList<>();
        List<Map<String, String>> pendingPrs = new ArrayList<>();
        int mergedCount = 0;
        int totalPrs = 0;

        for (JsonNode pr : prsNode) {
            totalPrs++;
            String state = pr.get("state").asText();
            String title = pr.get("title").asText();
            int number = pr.get("number").asInt();
            String prCreatedAt = pr.get("createdAt").asText();
            String prAuthor = extractPrAuthorLogin(pr);

            // 현재 OPEN이면서 리뷰 없는 PR → 대기 중
            if ("OPEN".equals(state)) {
                long waitingDays = ChronoUnit.DAYS.between(
                        ZonedDateTime.parse(prCreatedAt), ZonedDateTime.now());
                Map<String, String> pending = new LinkedHashMap<>();
                pending.put("number", String.valueOf(number));
                pending.put("title", title);
                pending.put("author", prAuthor);
                pending.put("waitingDays", String.valueOf(waitingDays));
                pendingPrs.add(pending);
            }

            if ("MERGED".equals(state)) {
                mergedCount++;
                String mergedAt = pr.get("mergedAt").asText();
                try {
                    long mergeHours = ChronoUnit.HOURS.between(
                            ZonedDateTime.parse(prCreatedAt), ZonedDateTime.parse(mergedAt));
                    if (mergeHours >= 0) timeToMergeHours.add(mergeHours);
                } catch (Exception e) {
                    log.debug("Failed to parse merge time for PR #{}", number);
                }
            }

            // 리뷰 분석
            JsonNode reviewsNode = pr.get("reviews");
            if (reviewsNode == null || !reviewsNode.has("nodes")) continue;
            JsonNode reviews = reviewsNode.get("nodes");

            if (reviews.size() > 0) {
                JsonNode firstReview = reviews.get(0);
                try {
                    String firstReviewAt = firstReview.get("createdAt").asText();
                    long hours = ChronoUnit.HOURS.between(
                            ZonedDateTime.parse(prCreatedAt), ZonedDateTime.parse(firstReviewAt));
                    if (hours >= 0) timeToFirstReviewHours.add(hours);
                } catch (Exception e) {
                    log.debug("Failed to parse first review time for PR #{}", number);
                }
            }

            for (JsonNode review : reviews) {
                String reviewer = "Unknown";
                if (review.has("author") && !review.get("author").isNull()) {
                    reviewer = review.get("author").get("login").asText();
                }
                try {
                    long hours = ChronoUnit.HOURS.between(
                            ZonedDateTime.parse(prCreatedAt),
                            ZonedDateTime.parse(review.get("createdAt").asText()));
                    if (hours >= 0) {
                        reviewerResponseHours.computeIfAbsent(reviewer, k -> new ArrayList<>()).add(hours);
                    }
                } catch (Exception e) {
                    log.debug("Failed to parse reviewer response time");
                }
            }
        }

        double avgFirstReview = timeToFirstReviewHours.stream().mapToLong(l -> l).average().orElse(0);
        double avgMerge = timeToMergeHours.stream().mapToLong(l -> l).average().orElse(0);

        StringBuilder sb = new StringBuilder();
        sb.append(String.format("## %s/%s PR 리뷰 병목 진단\n\n", owner, repo));
        sb.append(String.format("- 분석 대상: 최근 PR %d개 (머지됨: %d개)\n", totalPrs, mergedCount));
        sb.append(String.format("- 평균 첫 리뷰까지: **%.1f시간** (%.1f일)\n", avgFirstReview, avgFirstReview / 24));
        sb.append(String.format("- 평균 머지까지: **%.1f시간** (%.1f일)\n\n", avgMerge, avgMerge / 24));

        // DORA 기준 등급
        String leadTimeGrade;
        if (avgMerge < 24) leadTimeGrade = "Elite (< 1일)";
        else if (avgMerge < 168) leadTimeGrade = "High (1~7일)";
        else if (avgMerge < 720) leadTimeGrade = "Medium (1주~1개월)";
        else leadTimeGrade = "Low (> 1개월)";
        sb.append(String.format("DORA 리드 타임 등급: **%s**\n", leadTimeGrade));

        // 대기 중인 PR
        if (!pendingPrs.isEmpty()) {
            sb.append("\n### 현재 대기 중인 PR\n");
            sb.append("| PR | 제목 | 작성자 | 대기 일수 |\n");
            sb.append("|----|------|--------|----------|\n");
            for (Map<String, String> pr : pendingPrs) {
                sb.append(String.format("| #%s | %s | @%s | %s일 |\n",
                        pr.get("number"), pr.get("title"), pr.get("author"), pr.get("waitingDays")));
            }
        }

        // 리뷰어별 응답 시간
        if (!reviewerResponseHours.isEmpty()) {
            sb.append("\n### 리뷰어별 평균 응답 시간\n");
            sb.append("| 리뷰어 | 리뷰 수 | 평균 응답 시간 | 상태 |\n");
            sb.append("|--------|---------|--------------|------|\n");

            reviewerResponseHours.entrySet().stream()
                    .sorted((a, b) -> Double.compare(
                            a.getValue().stream().mapToLong(l -> l).average().orElse(0),
                            b.getValue().stream().mapToLong(l -> l).average().orElse(0)))
                    .forEach(entry -> {
                        double avgHours = entry.getValue().stream().mapToLong(l -> l).average().orElse(0);
                        String status = avgHours < 4 ? "빠름" :
                                        avgHours < 24 ? "양호" :
                                        avgHours < 72 ? "느림" : "매우 느림";
                        sb.append(String.format("| @%s | %d | %.1f시간 | %s |\n",
                                entry.getKey(), entry.getValue().size(), avgHours, status));
                    });
        }

        return sb.toString();
    }

    /**
     * 번아웃 위험 감지 — 커밋 시간대 패턴 변화 분석
     */
    public String diagnoseBurnoutRisk(String token, String owner, String repo) {
        JsonNode data = graphQLClient.getRepositoryAnalysis(token, owner, repo);
        JsonNode repoNode = data.get("repository");
        if (repoNode == null) {
            throw new RuntimeException("Repository not found: " + owner + "/" + repo);
        }

        JsonNode commitsNode = repoNode.get("defaultBranchRef").get("target").get("history").get("nodes");

        // 기여자별 커밋 시간대 분석
        Map<String, List<ZonedDateTime>> authorTimestamps = new LinkedHashMap<>();

        for (JsonNode commit : commitsNode) {
            String author = extractAuthorLogin(commit);
            try {
                ZonedDateTime dt = ZonedDateTime.parse(commit.get("committedDate").asText());
                authorTimestamps.computeIfAbsent(author, k -> new ArrayList<>()).add(dt);
            } catch (Exception e) {
                log.debug("Failed to parse commit date for author {}", author);
            }
        }

        StringBuilder sb = new StringBuilder();
        sb.append(String.format("## %s/%s 번아웃 위험 분석\n\n", owner, repo));

        if (authorTimestamps.isEmpty()) {
            sb.append("분석할 커밋 데이터가 없습니다.");
            return sb.toString();
        }

        sb.append("### 기여자별 업무 패턴\n\n");

        for (Map.Entry<String, List<ZonedDateTime>> entry : authorTimestamps.entrySet()) {
            String author = entry.getKey();
            List<ZonedDateTime> timestamps = entry.getValue();

            if (timestamps.size() < 3) continue;

            // 시간대별 분포
            int nightCommits = 0;   // 22시~06시
            int weekendCommits = 0;
            int totalCommits = timestamps.size();

            // 시간대 히스토그램 (0~23)
            int[] hourDistribution = new int[24];

            for (ZonedDateTime dt : timestamps) {
                int hour = dt.getHour();
                hourDistribution[hour]++;

                if (hour >= 22 || hour < 6) nightCommits++;

                int dayOfWeek = dt.getDayOfWeek().getValue(); // 1=MON, 7=SUN
                if (dayOfWeek >= 6) weekendCommits++;
            }

            double nightPct = (double) nightCommits / totalCommits * 100;
            double weekendPct = (double) weekendCommits / totalCommits * 100;

            // 위험도 판정
            String riskLevel;
            if (nightPct > 40 || weekendPct > 40) riskLevel = "높음";
            else if (nightPct > 25 || weekendPct > 25) riskLevel = "주의";
            else riskLevel = "정상";

            sb.append(String.format("#### @%s — 위험도: **%s**\n", author, riskLevel));
            sb.append(String.format("- 총 커밋: %d개\n", totalCommits));
            sb.append(String.format("- 야간 커밋 (22시~06시): %d개 (%.1f%%)\n", nightCommits, nightPct));
            sb.append(String.format("- 주말 커밋: %d개 (%.1f%%)\n", weekendCommits, weekendPct));

            // 주요 활동 시간대 (상위 3개)
            List<Integer> topHours = new ArrayList<>();
            for (int h = 0; h < 24; h++) topHours.add(h);
            topHours.sort((a, b) -> hourDistribution[b] - hourDistribution[a]);

            sb.append("- 주요 활동 시간: ");
            topHours.stream().limit(3)
                    .filter(h -> hourDistribution[h] > 0)
                    .forEach(h -> sb.append(String.format("%d시(%d건) ", h, hourDistribution[h])));
            sb.append("\n");

            // 시간대 시각화 (간단 바 차트)
            sb.append("- 시간대 분포: `");
            for (int h = 0; h < 24; h++) {
                if (hourDistribution[h] == 0) sb.append("·");
                else if (hourDistribution[h] <= 2) sb.append("▁");
                else if (hourDistribution[h] <= 4) sb.append("▃");
                else if (hourDistribution[h] <= 6) sb.append("▅");
                else sb.append("█");
            }
            sb.append("` (0시~23시)\n");

            if ("높음".equals(riskLevel)) {
                sb.append("\n**경고**: 야간/주말 커밋 비율이 높습니다. 업무 강도 조절이 필요합니다.\n");
            }
            sb.append("\n");
        }

        return sb.toString();
    }

    /**
     * DORA 메트릭 계산
     * - 배포 빈도: main 머지 빈도
     * - 리드 타임: PR 생성 → 머지 시간
     * - 변경 실패율: fix/revert 커밋 비율
     * - MTTR: 버그 이슈 생성 → 해결 시간
     */
    public String calculateDoraMetrics(String token, String owner, String repo) {
        JsonNode data = graphQLClient.getRepositoryAnalysis(token, owner, repo);
        JsonNode repoNode = data.get("repository");
        if (repoNode == null) {
            throw new RuntimeException("Repository not found: " + owner + "/" + repo);
        }

        JsonNode commitsNode = repoNode.get("defaultBranchRef").get("target").get("history").get("nodes");
        JsonNode prsNode = repoNode.get("pullRequests").get("nodes");
        JsonNode issuesNode = repoNode.get("issues").get("nodes");

        ZonedDateTime now = ZonedDateTime.now();
        ZonedDateTime thirtyDaysAgo = now.minusDays(30);

        // 1. 배포 빈도 — 최근 30일 머지된 PR 수
        int mergedLast30Days = 0;
        List<Long> leadTimes = new ArrayList<>();

        for (JsonNode pr : prsNode) {
            if (!"MERGED".equals(pr.get("state").asText())) continue;

            String mergedAt = pr.get("mergedAt").asText();
            String createdAt = pr.get("createdAt").asText();
            try {
                ZonedDateTime mergedDt = ZonedDateTime.parse(mergedAt);
                ZonedDateTime createdDt = ZonedDateTime.parse(createdAt);

                if (mergedDt.isAfter(thirtyDaysAgo)) {
                    mergedLast30Days++;
                }

                // 2. 리드 타임
                long hours = ChronoUnit.HOURS.between(createdDt, mergedDt);
                if (hours >= 0) leadTimes.add(hours);
            } catch (Exception e) {
                log.debug("Failed to parse PR dates");
            }
        }

        double avgLeadTimeHours = leadTimes.stream().mapToLong(l -> l).average().orElse(0);

        // 3. 변경 실패율 — fix/revert 커밋 비율
        int totalCommits = 0;
        int fixCommits = 0;
        for (JsonNode commit : commitsNode) {
            totalCommits++;
            String message = commit.get("message").asText();
            if (FIX_PATTERN.matcher(message).find()) {
                fixCommits++;
            }
        }
        double changeFailureRate = totalCommits > 0 ? (double) fixCommits / totalCommits * 100 : 0;

        // 4. MTTR — bug/fix 이슈의 생성 → 해결 시간
        List<Long> mttrHours = new ArrayList<>();
        for (JsonNode issue : issuesNode) {
            if (!"CLOSED".equals(issue.get("state").asText())) continue;

            String title = issue.get("title").asText().toLowerCase();
            String body = issue.has("body") && !issue.get("body").isNull()
                    ? issue.get("body").asText().toLowerCase() : "";

            if (title.contains("bug") || title.contains("fix") || title.contains("error")
                    || body.contains("bug") || body.contains("error")) {
                try {
                    ZonedDateTime created = ZonedDateTime.parse(issue.get("createdAt").asText());
                    ZonedDateTime closed = ZonedDateTime.parse(issue.get("closedAt").asText());
                    long hours = ChronoUnit.HOURS.between(created, closed);
                    if (hours >= 0) mttrHours.add(hours);
                } catch (Exception e) {
                    log.debug("Failed to parse issue dates");
                }
            }
        }
        double avgMttrHours = mttrHours.stream().mapToLong(l -> l).average().orElse(0);

        // DORA 등급 산정
        String dfGrade = mergedLast30Days >= 30 ? "Elite" :
                          mergedLast30Days >= 4 ? "High" :
                          mergedLast30Days >= 1 ? "Medium" : "Low";

        String ltGrade = avgLeadTimeHours < 24 ? "Elite" :
                          avgLeadTimeHours < 168 ? "High" :
                          avgLeadTimeHours < 720 ? "Medium" : "Low";

        String cfrGrade = changeFailureRate < 5 ? "Elite" :
                           changeFailureRate < 10 ? "High" :
                           changeFailureRate < 15 ? "Medium" : "Low";

        String mttrGrade = avgMttrHours == 0 ? "N/A" :
                            avgMttrHours < 1 ? "Elite" :
                            avgMttrHours < 24 ? "High" :
                            avgMttrHours < 168 ? "Medium" : "Low";

        StringBuilder sb = new StringBuilder();
        sb.append(String.format("## %s/%s DORA 메트릭\n\n", owner, repo));
        sb.append("| 지표 | 값 | 등급 | Elite 기준 |\n");
        sb.append("|------|-----|------|------------|\n");
        sb.append(String.format("| 배포 빈도 | %d회/30일 (%.1f회/주) | %s | 하루 여러 번 |\n",
                mergedLast30Days, mergedLast30Days / 4.3, dfGrade));
        sb.append(String.format("| 리드 타임 | %.1f시간 (%.1f일) | %s | < 1일 |\n",
                avgLeadTimeHours, avgLeadTimeHours / 24, ltGrade));
        sb.append(String.format("| 변경 실패율 | %.1f%% (%d/%d) | %s | < 5%% |\n",
                changeFailureRate, fixCommits, totalCommits, cfrGrade));
        sb.append(String.format("| MTTR | %.1f시간 (%.1f일) | %s | < 1시간 |\n",
                avgMttrHours, avgMttrHours / 24, mttrGrade));

        // 종합 등급
        int eliteCount = 0;
        if ("Elite".equals(dfGrade)) eliteCount++;
        if ("Elite".equals(ltGrade)) eliteCount++;
        if ("Elite".equals(cfrGrade)) eliteCount++;
        if ("Elite".equals(mttrGrade)) eliteCount++;

        String overallGrade;
        if (eliteCount >= 3) overallGrade = "Elite";
        else if (eliteCount >= 2) overallGrade = "High";
        else if (eliteCount >= 1) overallGrade = "Medium";
        else overallGrade = "Low";

        sb.append(String.format("\n**종합 DORA 등급: %s**\n", overallGrade));

        // 개선 제안
        sb.append("\n### 개선 제안\n");
        if ("Low".equals(dfGrade) || "Medium".equals(dfGrade)) {
            sb.append("- 배포 빈도를 높이려면: PR 크기를 줄이고, CI/CD 파이프라인을 자동화하세요.\n");
        }
        if ("Low".equals(ltGrade) || "Medium".equals(ltGrade)) {
            sb.append("- 리드 타임을 줄이려면: 코드 리뷰 프로세스를 개선하고, 리뷰어 지정을 자동화하세요.\n");
        }
        if ("Low".equals(cfrGrade) || "Medium".equals(cfrGrade)) {
            sb.append("- 변경 실패율을 줄이려면: 테스트 커버리지를 높이고, 스테이징 환경을 활용하세요.\n");
        }
        if ("Low".equals(mttrGrade) || "Medium".equals(mttrGrade)) {
            sb.append("- MTTR을 줄이려면: 모니터링/알림 체계를 구축하고, 장애 대응 매뉴얼을 문서화하세요.\n");
        }

        return sb.toString();
    }

    /**
     * 최근 N일 활동 요약
     */
    public String getRecentActivity(String token, String owner, String repo, int days) {
        JsonNode data = graphQLClient.getRepositoryAnalysis(token, owner, repo);
        JsonNode repoNode = data.get("repository");
        if (repoNode == null) {
            throw new RuntimeException("Repository not found: " + owner + "/" + repo);
        }

        ZonedDateTime cutoff = ZonedDateTime.now().minusDays(days);

        JsonNode commitsNode = repoNode.get("defaultBranchRef").get("target").get("history").get("nodes");
        JsonNode prsNode = repoNode.get("pullRequests").get("nodes");
        JsonNode issuesNode = repoNode.get("issues").get("nodes");

        // 최근 커밋
        List<String> recentCommits = new ArrayList<>();
        Map<String, Integer> commitAuthors = new LinkedHashMap<>();
        for (JsonNode commit : commitsNode) {
            try {
                ZonedDateTime dt = ZonedDateTime.parse(commit.get("committedDate").asText());
                if (dt.isAfter(cutoff)) {
                    String msg = commit.get("message").asText().split("\n")[0];
                    String author = extractAuthorLogin(commit);
                    recentCommits.add(String.format("- `%s` — %s (@%s)",
                            commit.get("oid").asText().substring(0, 7), msg, author));
                    commitAuthors.merge(author, 1, Integer::sum);
                }
            } catch (Exception e) {
                log.debug("Failed to parse commit date");
            }
        }

        // 최근 PR
        List<String> recentPrs = new ArrayList<>();
        int mergedPrs = 0, openPrs = 0;
        for (JsonNode pr : prsNode) {
            try {
                ZonedDateTime dt = ZonedDateTime.parse(pr.get("createdAt").asText());
                if (dt.isAfter(cutoff)) {
                    String state = pr.get("state").asText();
                    String stateIcon = switch (state) {
                        case "MERGED" -> "merged";
                        case "OPEN" -> "open";
                        default -> "closed";
                    };
                    if ("MERGED".equals(state)) mergedPrs++;
                    if ("OPEN".equals(state)) openPrs++;

                    String author = extractPrAuthorLogin(pr);
                    recentPrs.add(String.format("- #%d %s [%s] (@%s)",
                            pr.get("number").asInt(), pr.get("title").asText(), stateIcon, author));
                }
            } catch (Exception e) {
                log.debug("Failed to parse PR date");
            }
        }

        // 최근 이슈
        List<String> recentIssues = new ArrayList<>();
        int closedIssues = 0, openIssues = 0;
        for (JsonNode issue : issuesNode) {
            try {
                ZonedDateTime dt = ZonedDateTime.parse(issue.get("createdAt").asText());
                if (dt.isAfter(cutoff)) {
                    String state = issue.get("state").asText();
                    if ("CLOSED".equals(state)) closedIssues++;
                    else openIssues++;

                    String author = "Unknown";
                    if (issue.has("author") && !issue.get("author").isNull()) {
                        author = issue.get("author").get("login").asText();
                    }
                    recentIssues.add(String.format("- #%d %s [%s] (@%s)",
                            issue.get("number").asInt(), issue.get("title").asText(),
                            state.toLowerCase(), author));
                }
            } catch (Exception e) {
                log.debug("Failed to parse issue date");
            }
        }

        StringBuilder sb = new StringBuilder();
        sb.append(String.format("## %s/%s 최근 %d일 활동 요약\n\n", owner, repo, days));
        sb.append(String.format("### 요약 통계\n"));
        sb.append(String.format("- 커밋: %d개 (기여자 %d명)\n", recentCommits.size(), commitAuthors.size()));
        sb.append(String.format("- PR: %d개 (머지 %d / 오픈 %d)\n",
                recentPrs.size(), mergedPrs, openPrs));
        sb.append(String.format("- 이슈: %d개 (해결 %d / 오픈 %d)\n\n",
                recentIssues.size(), closedIssues, openIssues));

        // 기여자별 커밋 수
        if (!commitAuthors.isEmpty()) {
            sb.append("### 기여자별 커밋\n");
            commitAuthors.entrySet().stream()
                    .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                    .forEach(e -> sb.append(String.format("- @%s: %d개\n", e.getKey(), e.getValue())));
            sb.append("\n");
        }

        // 커밋 목록 (최대 15개)
        if (!recentCommits.isEmpty()) {
            sb.append("### 최근 커밋\n");
            recentCommits.stream().limit(15).forEach(c -> sb.append(c).append("\n"));
            if (recentCommits.size() > 15) {
                sb.append(String.format("... 외 %d개\n", recentCommits.size() - 15));
            }
            sb.append("\n");
        }

        // PR 목록
        if (!recentPrs.isEmpty()) {
            sb.append("### PR 활동\n");
            recentPrs.forEach(p -> sb.append(p).append("\n"));
            sb.append("\n");
        }

        // 이슈 목록
        if (!recentIssues.isEmpty()) {
            sb.append("### 이슈 활동\n");
            recentIssues.forEach(i -> sb.append(i).append("\n"));
        }

        return sb.toString();
    }

    /**
     * 스프린트 리포트 자동 생성 — 최근 N일간의 종합 요약
     */
    public String generateSprintReport(String token, String owner, String repo, int days) {
        JsonNode data = graphQLClient.getRepositoryAnalysis(token, owner, repo);
        JsonNode repoNode = data.get("repository");
        if (repoNode == null) {
            throw new RuntimeException("Repository not found: " + owner + "/" + repo);
        }

        ZonedDateTime now = ZonedDateTime.now();
        ZonedDateTime cutoff = now.minusDays(days);

        JsonNode commitsNode = repoNode.get("defaultBranchRef").get("target").get("history").get("nodes");
        JsonNode prsNode = repoNode.get("pullRequests").get("nodes");
        JsonNode issuesNode = repoNode.get("issues").get("nodes");

        // 커밋 분류 (Conventional Commits)
        Map<String, List<String>> commitsByType = new LinkedHashMap<>();
        Map<String, Integer> authorCommits = new LinkedHashMap<>();
        int totalCommits = 0;

        for (JsonNode commit : commitsNode) {
            try {
                ZonedDateTime dt = ZonedDateTime.parse(commit.get("committedDate").asText());
                if (!dt.isAfter(cutoff)) continue;
            } catch (Exception e) {
                continue;
            }

            totalCommits++;
            String message = commit.get("message").asText().split("\n")[0];
            String author = extractAuthorLogin(commit);
            authorCommits.merge(author, 1, Integer::sum);

            String type = classifyCommitType(message);
            commitsByType.computeIfAbsent(type, k -> new ArrayList<>()).add(message);
        }

        // PR 분류
        List<String> mergedPrs = new ArrayList<>();
        List<String> openPrs = new ArrayList<>();
        for (JsonNode pr : prsNode) {
            try {
                ZonedDateTime dt = ZonedDateTime.parse(pr.get("createdAt").asText());
                if (!dt.isAfter(cutoff)) continue;
            } catch (Exception e) {
                continue;
            }

            String state = pr.get("state").asText();
            String entry = String.format("#%d %s", pr.get("number").asInt(), pr.get("title").asText());

            if ("MERGED".equals(state)) mergedPrs.add(entry);
            else if ("OPEN".equals(state)) openPrs.add(entry);
        }

        // 이슈 분류
        List<String> closedIssues = new ArrayList<>();
        List<String> openIssues = new ArrayList<>();
        for (JsonNode issue : issuesNode) {
            try {
                ZonedDateTime dt = ZonedDateTime.parse(issue.get("createdAt").asText());
                if (!dt.isAfter(cutoff)) continue;
            } catch (Exception e) {
                continue;
            }

            String state = issue.get("state").asText();
            String entry = String.format("#%d %s", issue.get("number").asInt(), issue.get("title").asText());

            if ("CLOSED".equals(state)) closedIssues.add(entry);
            else openIssues.add(entry);
        }

        StringBuilder sb = new StringBuilder();
        sb.append(String.format("## %s/%s 스프린트 리포트\n", owner, repo));
        sb.append(String.format("**기간**: %s ~ %s (%d일)\n\n",
                cutoff.toLocalDate(), now.toLocalDate(), days));

        // 완료된 작업 (feat, fix 중심)
        sb.append("### 완료된 작업\n");
        if (commitsByType.containsKey("feat")) {
            sb.append("**기능 추가:**\n");
            commitsByType.get("feat").forEach(m -> sb.append("- ").append(m).append("\n"));
        }
        if (commitsByType.containsKey("fix")) {
            sb.append("**버그 수정:**\n");
            commitsByType.get("fix").forEach(m -> sb.append("- ").append(m).append("\n"));
        }
        if (commitsByType.containsKey("refactor")) {
            sb.append("**리팩토링:**\n");
            commitsByType.get("refactor").forEach(m -> sb.append("- ").append(m).append("\n"));
        }
        if (commitsByType.containsKey("docs")) {
            sb.append("**문서:**\n");
            commitsByType.get("docs").forEach(m -> sb.append("- ").append(m).append("\n"));
        }

        // 머지된 PR
        if (!mergedPrs.isEmpty()) {
            sb.append("\n### 머지된 PR\n");
            mergedPrs.forEach(p -> sb.append("- ").append(p).append("\n"));
        }

        // 진행 중 (오픈 PR)
        if (!openPrs.isEmpty()) {
            sb.append("\n### 진행 중 (오픈 PR)\n");
            openPrs.forEach(p -> sb.append("- ").append(p).append("\n"));
        }

        // 해결된 이슈
        if (!closedIssues.isEmpty()) {
            sb.append("\n### 해결된 이슈\n");
            closedIssues.forEach(i -> sb.append("- ").append(i).append("\n"));
        }

        // 미해결 이슈
        if (!openIssues.isEmpty()) {
            sb.append("\n### 미해결 이슈\n");
            openIssues.forEach(i -> sb.append("- ").append(i).append("\n"));
        }

        // 팀 통계
        sb.append("\n### 팀 통계\n");
        sb.append(String.format("- 총 커밋: %d개\n", totalCommits));
        sb.append(String.format("- 머지된 PR: %d개\n", mergedPrs.size()));
        sb.append(String.format("- 해결된 이슈: %d개\n", closedIssues.size()));
        if (!authorCommits.isEmpty()) {
            sb.append("- 기여자별: ");
            authorCommits.entrySet().stream()
                    .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                    .forEach(e -> sb.append(String.format("@%s(%d) ", e.getKey(), e.getValue())));
            sb.append("\n");
        }

        return sb.toString();
    }

    // --- Helper methods ---

    private String extractAuthorLogin(JsonNode commit) {
        JsonNode authorNode = commit.get("author");
        if (authorNode != null && authorNode.has("user") && !authorNode.get("user").isNull()) {
            return authorNode.get("user").get("login").asText();
        }
        if (authorNode != null && authorNode.has("name")) {
            return authorNode.get("name").asText();
        }
        return "Unknown";
    }

    private String extractPrAuthorLogin(JsonNode pr) {
        if (pr.has("author") && !pr.get("author").isNull()) {
            return pr.get("author").get("login").asText();
        }
        return "Unknown";
    }

    private String classifyCommitType(String message) {
        String lower = message.toLowerCase();
        if (lower.startsWith("feat")) return "feat";
        if (lower.startsWith("fix") || lower.startsWith("hotfix")) return "fix";
        if (lower.startsWith("refactor")) return "refactor";
        if (lower.startsWith("docs")) return "docs";
        if (lower.startsWith("test")) return "test";
        if (lower.startsWith("chore") || lower.startsWith("ci") || lower.startsWith("build")) return "chore";
        if (lower.startsWith("style")) return "style";
        if (lower.startsWith("perf")) return "perf";
        if (lower.startsWith("revert")) return "revert";
        return "other";
    }
}
