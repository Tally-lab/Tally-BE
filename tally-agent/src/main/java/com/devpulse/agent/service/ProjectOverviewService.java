package com.devpulse.agent.service;

import com.devpulse.agent.dto.ProjectOverviewResponse;
import com.devpulse.agent.dto.ProjectOverviewResponse.*;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Service
public class ProjectOverviewService {

    private final WebClient github;
    private final ObjectMapper mapper = new ObjectMapper();

    public ProjectOverviewService() {
        this.github = WebClient.builder()
                .baseUrl("https://api.github.com")
                .defaultHeader(HttpHeaders.ACCEPT, "application/vnd.github.v3+json")
                .codecs(c -> c.defaultCodecs().maxInMemorySize(10 * 1024 * 1024))
                .build();
    }

    public ProjectOverviewResponse getOverview(String token, String owner, String repo) {
        log.info("Fetching project overview for {}/{}", owner, repo);

        RepoInfo repoInfo = fetchRepoInfo(token, owner, repo);
        List<ContributorInfo> contributors = fetchContributors(token, owner, repo);
        HealthIndicators health = buildHealthIndicators(contributors, token, owner, repo);
        ActivitySummary activity = fetchActivity(token, owner, repo);
        Map<String, Double> languages = fetchLanguages(token, owner, repo);
        TechStackInfo techStack = analyzeTechStack(token, owner, repo);

        return new ProjectOverviewResponse(repoInfo, contributors, health, activity, languages, techStack);
    }

    private RepoInfo fetchRepoInfo(String token, String owner, String repo) {
        try {
            JsonNode node = get(token, "/repos/%s/%s".formatted(owner, repo));
            return new RepoInfo(
                    node.path("full_name").asText(),
                    node.path("description").asText(""),
                    node.path("default_branch").asText("main"),
                    node.path("stargazers_count").asInt(0),
                    node.path("forks_count").asInt(0),
                    node.path("open_issues_count").asInt(0)
            );
        } catch (Exception e) {
            log.warn("Failed to fetch repo info: {}", e.getMessage());
            return new RepoInfo(owner + "/" + repo, "", "main", 0, 0, 0);
        }
    }

    private List<ContributorInfo> fetchContributors(String token, String owner, String repo) {
        try {
            JsonNode nodes = get(token, "/repos/%s/%s/contributors?per_page=20".formatted(owner, repo));
            int totalCommits = 0;
            List<ContributorInfo> list = new ArrayList<>();

            for (JsonNode n : nodes) {
                totalCommits += n.path("contributions").asInt(0);
            }

            for (JsonNode n : nodes) {
                int commits = n.path("contributions").asInt(0);
                double pct = totalCommits > 0 ? Math.round(commits * 1000.0 / totalCommits) / 10.0 : 0;
                list.add(new ContributorInfo(
                        n.path("login").asText(),
                        n.path("avatar_url").asText(""),
                        commits,
                        pct
                ));
            }
            return list;
        } catch (Exception e) {
            log.warn("Failed to fetch contributors: {}", e.getMessage());
            return List.of();
        }
    }

    private HealthIndicators buildHealthIndicators(List<ContributorInfo> contributors, String token, String owner, String repo) {
        // Bus Factor: count contributors needed to reach 70% of commits
        int busFactor = 0;
        double cumulative = 0;
        for (ContributorInfo c : contributors) {
            cumulative += c.percentage();
            busFactor++;
            if (cumulative >= 70.0) break;
        }
        String bfStatus = busFactor >= 4 ? "양호" : busFactor >= 3 ? "보통" : busFactor >= 2 ? "주의" : "위험";

        // Open PRs
        List<PullRequestInfo> pendingPRs = new ArrayList<>();
        try {
            JsonNode prs = get(token, "/repos/%s/%s/pulls?state=open&per_page=10".formatted(owner, repo));
            for (JsonNode pr : prs) {
                pendingPRs.add(new PullRequestInfo(
                        pr.path("number").asInt(),
                        pr.path("title").asText(),
                        pr.path("user").path("login").asText(),
                        pr.path("created_at").asText()
                ));
            }
        } catch (Exception e) {
            log.warn("Failed to fetch open PRs: {}", e.getMessage());
        }

        return new HealthIndicators(
                new BusFactorInfo(busFactor, bfStatus),
                new ReviewStatus(pendingPRs.size(), pendingPRs)
        );
    }

    private ActivitySummary fetchActivity(String token, String owner, String repo) {
        String since = Instant.now().minus(7, ChronoUnit.DAYS).toString();
        int commits = 0, prsOpened = 0, prsMerged = 0;

        try {
            JsonNode commitNodes = get(token, "/repos/%s/%s/commits?since=%s&per_page=100".formatted(owner, repo, since));
            commits = commitNodes.size();
        } catch (Exception e) {
            log.warn("Failed to fetch recent commits: {}", e.getMessage());
        }

        try {
            JsonNode prNodes = get(token, "/repos/%s/%s/pulls?state=all&sort=created&direction=desc&per_page=50".formatted(owner, repo));
            for (JsonNode pr : prNodes) {
                String createdAt = pr.path("created_at").asText("");
                if (createdAt.compareTo(since) >= 0) {
                    prsOpened++;
                    if (pr.path("merged_at").asText("").compareTo(since) >= 0) {
                        prsMerged++;
                    }
                }
            }
        } catch (Exception e) {
            log.warn("Failed to fetch recent PRs: {}", e.getMessage());
        }

        return new ActivitySummary(commits, prsOpened, prsMerged);
    }

    private Map<String, Double> fetchLanguages(String token, String owner, String repo) {
        try {
            JsonNode node = get(token, "/repos/%s/%s/languages".formatted(owner, repo));
            long total = 0;
            Map<String, Long> raw = new LinkedHashMap<>();
            node.fields().forEachRemaining(e -> raw.put(e.getKey(), e.getValue().asLong()));
            for (long v : raw.values()) total += v;

            Map<String, Double> result = new LinkedHashMap<>();
            for (var e : raw.entrySet()) {
                result.put(e.getKey(), total > 0 ? Math.round(e.getValue() * 1000.0 / total) / 10.0 : 0);
            }
            return result;
        } catch (Exception e) {
            log.warn("Failed to fetch languages: {}", e.getMessage());
            return Map.of();
        }
    }

    // ── Tech Stack Analysis (lightweight, same logic as MCP TechStackService) ──

    private static final Map<String, String[]> DOCS = new LinkedHashMap<>();
    static {
        // [displayName, category, docsUrl]
        DOCS.put("spring-boot", new String[]{"Spring Boot", "프레임워크", "https://docs.spring.io/spring-boot/"});
        DOCS.put("spring-ai", new String[]{"Spring AI", "프레임워크", "https://docs.spring.io/spring-ai/reference/"});
        DOCS.put("spring-security", new String[]{"Spring Security", "프레임워크", "https://docs.spring.io/spring-security/reference/"});
        DOCS.put("spring-data-jpa", new String[]{"Spring Data JPA", "프레임워크", "https://docs.spring.io/spring-data/jpa/reference/"});
        DOCS.put("react", new String[]{"React", "프레임워크", "https://react.dev/"});
        DOCS.put("next", new String[]{"Next.js", "프레임워크", "https://nextjs.org/docs"});
        DOCS.put("vue", new String[]{"Vue.js", "프레임워크", "https://vuejs.org/guide/"});
        DOCS.put("angular", new String[]{"Angular", "프레임워크", "https://angular.dev/"});
        DOCS.put("express", new String[]{"Express", "프레임워크", "https://expressjs.com/"});
        DOCS.put("nestjs", new String[]{"NestJS", "프레임워크", "https://docs.nestjs.com/"});
        DOCS.put("django", new String[]{"Django", "프레임워크", "https://docs.djangoproject.com/"});
        DOCS.put("fastapi", new String[]{"FastAPI", "프레임워크", "https://fastapi.tiangolo.com/"});
        DOCS.put("flask", new String[]{"Flask", "프레임워크", "https://flask.palletsprojects.com/"});
        DOCS.put("typescript", new String[]{"TypeScript", "언어", "https://www.typescriptlang.org/docs/"});
        DOCS.put("vite", new String[]{"Vite", "빌드 도구", "https://vitejs.dev/guide/"});
        DOCS.put("tailwindcss", new String[]{"Tailwind CSS", "라이브러리", "https://tailwindcss.com/docs"});
        DOCS.put("axios", new String[]{"Axios", "라이브러리", "https://axios-http.com/docs/intro"});
        DOCS.put("recharts", new String[]{"Recharts", "라이브러리", "https://recharts.org/en-US/api"});
        DOCS.put("react-router-dom", new String[]{"React Router", "라이브러리", "https://reactrouter.com/"});
        DOCS.put("prisma", new String[]{"Prisma", "라이브러리", "https://www.prisma.io/docs"});
        DOCS.put("jackson", new String[]{"Jackson", "라이브러리", "https://github.com/FasterXML/jackson"});
        DOCS.put("lombok", new String[]{"Lombok", "라이브러리", "https://projectlombok.org/features/"});
        DOCS.put("client-java", new String[]{"Kubernetes Java Client", "라이브러리", "https://github.com/kubernetes-client/java"});
        DOCS.put("numpy", new String[]{"NumPy", "라이브러리", "https://numpy.org/doc/stable/"});
        DOCS.put("pandas", new String[]{"pandas", "라이브러리", "https://pandas.pydata.org/docs/"});
        DOCS.put("torch", new String[]{"PyTorch", "라이브러리", "https://pytorch.org/docs/stable/"});
        DOCS.put("langchain", new String[]{"LangChain", "라이브러리", "https://python.langchain.com/docs/"});
        DOCS.put("zustand", new String[]{"Zustand", "라이브러리", "https://zustand-demo.pmnd.rs/"});
        DOCS.put("tanstack", new String[]{"TanStack Query", "라이브러리", "https://tanstack.com/query/latest/docs/"});
    }

    private TechStackInfo analyzeTechStack(String token, String owner, String repo) {
        List<TechItem> items = new ArrayList<>();

        // package.json
        String pkg = fetchFileContent(token, owner, repo, "package.json");
        if (pkg != null) parsePackageJson(pkg, items);

        // build.gradle.kts or build.gradle
        String gradle = fetchFileContent(token, owner, repo, "build.gradle.kts");
        if (gradle == null) gradle = fetchFileContent(token, owner, repo, "build.gradle");
        if (gradle != null) parseGradle(gradle, items);

        // requirements.txt
        String reqs = fetchFileContent(token, owner, repo, "requirements.txt");
        if (reqs != null) parseRequirements(reqs, items);

        // Deduplicate
        Map<String, TechItem> unique = new LinkedHashMap<>();
        for (TechItem item : items) {
            if (!unique.containsKey(item.name()) || (unique.get(item.name()).version().isEmpty() && !item.version().isEmpty())) {
                unique.put(item.name(), item);
            }
        }

        // Group by category
        Map<String, List<TechItem>> grouped = new LinkedHashMap<>();
        for (TechItem item : unique.values()) {
            // find category from DOCS
            String cat = "라이브러리";
            for (var e : DOCS.entrySet()) {
                if (e.getValue()[0].equals(item.name())) {
                    cat = e.getValue()[1];
                    break;
                }
            }
            grouped.computeIfAbsent(cat, k -> new ArrayList<>()).add(item);
        }

        List<TechCategory> categories = new ArrayList<>();
        for (String cat : List.of("언어", "프레임워크", "라이브러리", "빌드 도구", "데이터베이스", "인프라")) {
            List<TechItem> catItems = grouped.get(cat);
            if (catItems != null && !catItems.isEmpty()) {
                categories.add(new TechCategory(cat, catItems));
            }
        }

        return new TechStackInfo(unique.size(), categories);
    }

    private String fetchFileContent(String token, String owner, String repo, String path) {
        try {
            JsonNode node = get(token, "/repos/%s/%s/contents/%s".formatted(owner, repo, path));
            if (node != null && node.has("content")) {
                String encoded = node.get("content").asText().replaceAll("\\s", "");
                return new String(Base64.getDecoder().decode(encoded), StandardCharsets.UTF_8);
            }
        } catch (Exception e) {
            // File not found, expected
        }
        return null;
    }

    private void parsePackageJson(String content, List<TechItem> items) {
        try {
            JsonNode pkg = mapper.readTree(content);
            Map<String, String> allDeps = new LinkedHashMap<>();
            if (pkg.has("dependencies")) pkg.get("dependencies").fields().forEachRemaining(e -> allDeps.put(e.getKey(), e.getValue().asText()));
            if (pkg.has("devDependencies")) pkg.get("devDependencies").fields().forEachRemaining(e -> allDeps.put(e.getKey(), e.getValue().asText()));

            for (var dep : allDeps.entrySet()) {
                String[] mapping = findMapping(dep.getKey());
                if (mapping != null) {
                    String version = dep.getValue().replaceAll("[^0-9.]", "");
                    items.add(new TechItem(mapping[0], version, mapping[2], "package.json"));
                }
            }
        } catch (Exception e) {
            log.warn("Failed to parse package.json", e);
        }
    }

    private void parseGradle(String content, List<TechItem> items) {
        // Java version
        Matcher javaMatch = Pattern.compile("(?:sourceCompatibility|jvmToolchain\\()(\\d+)").matcher(content);
        if (javaMatch.find()) {
            items.add(new TechItem("Java", javaMatch.group(1), "https://docs.oracle.com/en/java/javase/" + javaMatch.group(1) + "/", "build.gradle"));
        }

        // Dependencies
        Matcher depMatch = Pattern.compile("(?:implementation|api|compileOnly|runtimeOnly)\\s*\\(?['\"]([^'\"]+)['\"]\\)?").matcher(content);
        while (depMatch.find()) {
            String[] parts = depMatch.group(1).split(":");
            if (parts.length >= 2) {
                String[] mapping = findMapping(parts[1]);
                if (mapping != null) {
                    String version = parts.length >= 3 ? parts[2] : "";
                    items.add(new TechItem(mapping[0], version, mapping[2], "build.gradle"));
                }
            }
        }
    }

    private void parseRequirements(String content, List<TechItem> items) {
        for (String line : content.split("\n")) {
            line = line.trim();
            if (line.isEmpty() || line.startsWith("#")) continue;
            String[] parts = line.split("[=<>!~]+");
            String name = parts[0].trim().toLowerCase();
            String version = parts.length > 1 ? parts[parts.length - 1].trim() : "";
            String[] mapping = findMapping(name);
            if (mapping != null) {
                items.add(new TechItem(mapping[0], version, mapping[2], "requirements.txt"));
            }
        }
    }

    private String[] findMapping(String name) {
        String lower = name.toLowerCase();
        if (DOCS.containsKey(lower)) return DOCS.get(lower);
        for (var e : DOCS.entrySet()) {
            if (lower.contains(e.getKey())) return e.getValue();
        }
        return null;
    }

    private JsonNode get(String token, String uri) {
        try {
            String response = github.get()
                    .uri(uri)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();
            return mapper.readTree(response);
        } catch (Exception e) {
            throw new RuntimeException("GitHub API call failed: " + uri, e);
        }
    }
}
