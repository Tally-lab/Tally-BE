package com.devpulse.mcp.github.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class TechStackService {

    private final GitHubRestClient restClient;
    private final ObjectMapper objectMapper = new ObjectMapper();

    private static final Map<String, DocMapping> DOCS_MAP = new LinkedHashMap<>();

    static {
        // Java / Spring
        DOCS_MAP.put("spring-boot", new DocMapping("Spring Boot", "프레임워크", "https://docs.spring.io/spring-boot/"));
        DOCS_MAP.put("spring-ai", new DocMapping("Spring AI", "프레임워크", "https://docs.spring.io/spring-ai/reference/"));
        DOCS_MAP.put("spring-security", new DocMapping("Spring Security", "프레임워크", "https://docs.spring.io/spring-security/reference/"));
        DOCS_MAP.put("spring-webflux", new DocMapping("Spring WebFlux", "프레임워크", "https://docs.spring.io/spring-framework/reference/web/webflux.html"));
        DOCS_MAP.put("spring-data-jpa", new DocMapping("Spring Data JPA", "프레임워크", "https://docs.spring.io/spring-data/jpa/reference/"));
        DOCS_MAP.put("spring-cloud", new DocMapping("Spring Cloud", "프레임워크", "https://docs.spring.io/spring-cloud/"));
        DOCS_MAP.put("jackson", new DocMapping("Jackson", "라이브러리", "https://github.com/FasterXML/jackson"));
        DOCS_MAP.put("lombok", new DocMapping("Lombok", "라이브러리", "https://projectlombok.org/features/"));
        DOCS_MAP.put("querydsl", new DocMapping("QueryDSL", "라이브러리", "http://querydsl.com/static/querydsl/latest/reference/html/"));
        DOCS_MAP.put("mapstruct", new DocMapping("MapStruct", "라이브러리", "https://mapstruct.org/documentation/stable/reference/html/"));
        DOCS_MAP.put("client-java", new DocMapping("Kubernetes Java Client", "라이브러리", "https://github.com/kubernetes-client/java"));

        // JavaScript / TypeScript
        DOCS_MAP.put("react", new DocMapping("React", "프레임워크", "https://react.dev/"));
        DOCS_MAP.put("react-dom", new DocMapping("React DOM", "프레임워크", "https://react.dev/reference/react-dom"));
        DOCS_MAP.put("next", new DocMapping("Next.js", "프레임워크", "https://nextjs.org/docs"));
        DOCS_MAP.put("vue", new DocMapping("Vue.js", "프레임워크", "https://vuejs.org/guide/"));
        DOCS_MAP.put("nuxt", new DocMapping("Nuxt", "프레임워크", "https://nuxt.com/docs"));
        DOCS_MAP.put("svelte", new DocMapping("Svelte", "프레임워크", "https://svelte.dev/docs"));
        DOCS_MAP.put("angular", new DocMapping("Angular", "프레임워크", "https://angular.dev/"));
        DOCS_MAP.put("express", new DocMapping("Express", "프레임워크", "https://expressjs.com/"));
        DOCS_MAP.put("nestjs", new DocMapping("NestJS", "프레임워크", "https://docs.nestjs.com/"));
        DOCS_MAP.put("typescript", new DocMapping("TypeScript", "언어", "https://www.typescriptlang.org/docs/"));
        DOCS_MAP.put("vite", new DocMapping("Vite", "빌드 도구", "https://vitejs.dev/guide/"));
        DOCS_MAP.put("tailwindcss", new DocMapping("Tailwind CSS", "라이브러리", "https://tailwindcss.com/docs"));
        DOCS_MAP.put("axios", new DocMapping("Axios", "라이브러리", "https://axios-http.com/docs/intro"));
        DOCS_MAP.put("recharts", new DocMapping("Recharts", "라이브러리", "https://recharts.org/en-US/api"));
        DOCS_MAP.put("react-router-dom", new DocMapping("React Router", "라이브러리", "https://reactrouter.com/"));
        DOCS_MAP.put("react-markdown", new DocMapping("react-markdown", "라이브러리", "https://github.com/remarkjs/react-markdown"));
        DOCS_MAP.put("zustand", new DocMapping("Zustand", "라이브러리", "https://zustand-demo.pmnd.rs/"));
        DOCS_MAP.put("tanstack", new DocMapping("TanStack Query", "라이브러리", "https://tanstack.com/query/latest/docs/"));
        DOCS_MAP.put("prisma", new DocMapping("Prisma", "라이브러리", "https://www.prisma.io/docs"));

        // Python
        DOCS_MAP.put("django", new DocMapping("Django", "프레임워크", "https://docs.djangoproject.com/"));
        DOCS_MAP.put("flask", new DocMapping("Flask", "프레임워크", "https://flask.palletsprojects.com/"));
        DOCS_MAP.put("fastapi", new DocMapping("FastAPI", "프레임워크", "https://fastapi.tiangolo.com/"));
        DOCS_MAP.put("numpy", new DocMapping("NumPy", "라이브러리", "https://numpy.org/doc/stable/"));
        DOCS_MAP.put("pandas", new DocMapping("pandas", "라이브러리", "https://pandas.pydata.org/docs/"));
        DOCS_MAP.put("torch", new DocMapping("PyTorch", "라이브러리", "https://pytorch.org/docs/stable/"));
        DOCS_MAP.put("tensorflow", new DocMapping("TensorFlow", "라이브러리", "https://www.tensorflow.org/api_docs"));
        DOCS_MAP.put("scikit-learn", new DocMapping("scikit-learn", "라이브러리", "https://scikit-learn.org/stable/documentation.html"));
        DOCS_MAP.put("langchain", new DocMapping("LangChain", "라이브러리", "https://python.langchain.com/docs/"));
        DOCS_MAP.put("sqlalchemy", new DocMapping("SQLAlchemy", "라이브러리", "https://docs.sqlalchemy.org/"));

        // Go
        DOCS_MAP.put("gin-gonic", new DocMapping("Gin", "프레임워크", "https://gin-gonic.com/docs/"));
        DOCS_MAP.put("echo", new DocMapping("Echo", "프레임워크", "https://echo.labstack.com/docs"));
        DOCS_MAP.put("fiber", new DocMapping("Fiber", "프레임워크", "https://docs.gofiber.io/"));

        // Rust
        DOCS_MAP.put("tokio", new DocMapping("Tokio", "프레임워크", "https://tokio.rs/tokio/tutorial"));
        DOCS_MAP.put("actix-web", new DocMapping("Actix Web", "프레임워크", "https://actix.rs/docs"));
        DOCS_MAP.put("serde", new DocMapping("Serde", "라이브러리", "https://serde.rs/"));

        // Database
        DOCS_MAP.put("postgresql", new DocMapping("PostgreSQL", "데이터베이스", "https://www.postgresql.org/docs/"));
        DOCS_MAP.put("mysql", new DocMapping("MySQL", "데이터베이스", "https://dev.mysql.com/doc/"));
        DOCS_MAP.put("mongodb", new DocMapping("MongoDB", "데이터베이스", "https://www.mongodb.com/docs/"));
        DOCS_MAP.put("redis", new DocMapping("Redis", "데이터베이스", "https://redis.io/docs/"));
        DOCS_MAP.put("h2", new DocMapping("H2 Database", "데이터베이스", "https://h2database.com/html/main.html"));
    }

    public String analyzeTechStack(String token, String owner, String repo) {
        log.info("Analyzing tech stack for {}/{}", owner, repo);

        List<TechEntry> techEntries = new ArrayList<>();

        // 1. package.json (Node.js / React / Vue 등)
        analyzePackageJson(token, owner, repo, techEntries);

        // 2. build.gradle / build.gradle.kts (Java / Spring)
        analyzeGradle(token, owner, repo, techEntries);

        // 3. pom.xml (Maven)
        analyzePomXml(token, owner, repo, techEntries);

        // 4. requirements.txt (Python)
        analyzeRequirementsTxt(token, owner, repo, techEntries);

        // 5. go.mod (Go)
        analyzeGoMod(token, owner, repo, techEntries);

        // 6. Cargo.toml (Rust)
        analyzeCargoToml(token, owner, repo, techEntries);

        // 7. Dockerfile
        analyzeDockerfile(token, owner, repo, techEntries);

        if (techEntries.isEmpty()) {
            return String.format("## %s/%s 기술 스택 분석\n\n의존성 파일을 찾을 수 없습니다. (package.json, build.gradle, pom.xml, requirements.txt, go.mod, Cargo.toml 모두 없음)", owner, repo);
        }

        return formatResult(owner, repo, techEntries);
    }

    private String getDecodedContent(String token, String owner, String repo, String path) {
        JsonNode node = restClient.getFileContent(token, owner, repo, path);
        if (node == null || !node.has("content")) return null;
        String encoded = node.get("content").asText().replaceAll("\\s", "");
        return new String(Base64.getDecoder().decode(encoded), StandardCharsets.UTF_8);
    }

    private void analyzePackageJson(String token, String owner, String repo, List<TechEntry> entries) {
        String content = getDecodedContent(token, owner, repo, "package.json");
        if (content == null) return;

        try {
            JsonNode pkg = objectMapper.readTree(content);
            Map<String, String> allDeps = new LinkedHashMap<>();

            if (pkg.has("dependencies")) {
                pkg.get("dependencies").fields().forEachRemaining(e -> allDeps.put(e.getKey(), e.getValue().asText()));
            }
            if (pkg.has("devDependencies")) {
                pkg.get("devDependencies").fields().forEachRemaining(e -> allDeps.put(e.getKey(), e.getValue().asText()));
            }

            for (Map.Entry<String, String> dep : allDeps.entrySet()) {
                String name = dep.getKey();
                String version = dep.getValue().replaceAll("[^0-9.]", "");
                DocMapping mapping = findDocMapping(name);
                if (mapping != null) {
                    entries.add(new TechEntry(mapping.displayName, version, mapping.category, mapping.docsUrl, "package.json"));
                }
            }
        } catch (Exception e) {
            log.warn("Failed to parse package.json for {}/{}", owner, repo, e);
        }
    }

    private void analyzeGradle(String token, String owner, String repo, List<TechEntry> entries) {
        String content = getDecodedContent(token, owner, repo, "build.gradle.kts");
        if (content == null) {
            content = getDecodedContent(token, owner, repo, "build.gradle");
        }
        if (content == null) return;

        // Java version
        var javaMatch = java.util.regex.Pattern.compile("(?:sourceCompatibility|java\\.sourceCompatibility|languageVersion\\.set\\(JavaLanguageVersion\\.of\\(|jvmToolchain\\()(\\d+)").matcher(content);
        if (javaMatch.find()) {
            entries.add(new TechEntry("Java", javaMatch.group(1), "언어", "https://docs.oracle.com/en/java/javase/" + javaMatch.group(1) + "/", "build.gradle"));
        }

        // Spring Boot version from plugins
        var bootMatch = java.util.regex.Pattern.compile("org\\.springframework\\.boot['\"]?\\)?\\s*version\\s*['\"]([^'\"]+)['\"]").matcher(content);
        if (bootMatch.find()) {
            entries.add(new TechEntry("Spring Boot", bootMatch.group(1), "프레임워크", "https://docs.spring.io/spring-boot/", "build.gradle"));
        }

        // Dependencies
        var depPattern = java.util.regex.Pattern.compile("(?:implementation|api|compileOnly|runtimeOnly|testImplementation)\\s*\\(?['\"]([^'\"]+)['\"]\\)?");
        var depMatcher = depPattern.matcher(content);
        while (depMatcher.find()) {
            String dep = depMatcher.group(1);
            String[] parts = dep.split(":");
            if (parts.length >= 2) {
                String artifactId = parts[1];
                String version = parts.length >= 3 ? parts[2] : "";
                DocMapping mapping = findDocMapping(artifactId);
                if (mapping != null) {
                    entries.add(new TechEntry(mapping.displayName, version, mapping.category, mapping.docsUrl, "build.gradle"));
                }
            }
        }

        // Spring AI BOM
        var bomMatch = java.util.regex.Pattern.compile("spring-ai-bom['\"]?\\)?[^\"']*['\"]([^'\"]+)['\"]").matcher(content);
        if (bomMatch.find()) {
            entries.add(new TechEntry("Spring AI", bomMatch.group(1), "프레임워크", "https://docs.spring.io/spring-ai/reference/", "build.gradle"));
        }
    }

    private void analyzePomXml(String token, String owner, String repo, List<TechEntry> entries) {
        String content = getDecodedContent(token, owner, repo, "pom.xml");
        if (content == null) return;

        var depPattern = java.util.regex.Pattern.compile("<artifactId>([^<]+)</artifactId>\\s*(?:<version>([^<]+)</version>)?");
        var depMatcher = depPattern.matcher(content);
        while (depMatcher.find()) {
            String artifactId = depMatcher.group(1);
            String version = depMatcher.group(2) != null ? depMatcher.group(2) : "";
            DocMapping mapping = findDocMapping(artifactId);
            if (mapping != null) {
                entries.add(new TechEntry(mapping.displayName, version, mapping.category, mapping.docsUrl, "pom.xml"));
            }
        }
    }

    private void analyzeRequirementsTxt(String token, String owner, String repo, List<TechEntry> entries) {
        String content = getDecodedContent(token, owner, repo, "requirements.txt");
        if (content == null) return;

        for (String line : content.split("\n")) {
            line = line.trim();
            if (line.isEmpty() || line.startsWith("#")) continue;
            String[] parts = line.split("[=<>!~]+");
            String name = parts[0].trim().toLowerCase();
            String version = parts.length > 1 ? parts[parts.length - 1].trim() : "";
            DocMapping mapping = findDocMapping(name);
            if (mapping != null) {
                entries.add(new TechEntry(mapping.displayName, version, mapping.category, mapping.docsUrl, "requirements.txt"));
            }
        }
    }

    private void analyzeGoMod(String token, String owner, String repo, List<TechEntry> entries) {
        String content = getDecodedContent(token, owner, repo, "go.mod");
        if (content == null) return;

        // Go version
        var goMatch = java.util.regex.Pattern.compile("^go\\s+(\\S+)", java.util.regex.Pattern.MULTILINE).matcher(content);
        if (goMatch.find()) {
            entries.add(new TechEntry("Go", goMatch.group(1), "언어", "https://go.dev/doc/", "go.mod"));
        }

        for (String line : content.split("\n")) {
            line = line.trim();
            if (line.startsWith("//") || line.isEmpty()) continue;
            for (String key : DOCS_MAP.keySet()) {
                if (line.contains(key)) {
                    DocMapping mapping = DOCS_MAP.get(key);
                    var versionMatch = java.util.regex.Pattern.compile("v([\\d.]+)").matcher(line);
                    String version = versionMatch.find() ? versionMatch.group(1) : "";
                    entries.add(new TechEntry(mapping.displayName, version, mapping.category, mapping.docsUrl, "go.mod"));
                    break;
                }
            }
        }
    }

    private void analyzeCargoToml(String token, String owner, String repo, List<TechEntry> entries) {
        String content = getDecodedContent(token, owner, repo, "Cargo.toml");
        if (content == null) return;

        var depPattern = java.util.regex.Pattern.compile("^(\\w[\\w-]*)\\s*=\\s*(?:\"([^\"]+)\"|\\{[^}]*version\\s*=\\s*\"([^\"]+)\")", java.util.regex.Pattern.MULTILINE);
        var depMatcher = depPattern.matcher(content);
        while (depMatcher.find()) {
            String name = depMatcher.group(1);
            String version = depMatcher.group(2) != null ? depMatcher.group(2) : depMatcher.group(3);
            DocMapping mapping = findDocMapping(name);
            if (mapping != null) {
                entries.add(new TechEntry(mapping.displayName, version != null ? version : "", mapping.category, mapping.docsUrl, "Cargo.toml"));
            }
        }
    }

    private void analyzeDockerfile(String token, String owner, String repo, List<TechEntry> entries) {
        String content = getDecodedContent(token, owner, repo, "Dockerfile");
        if (content == null) return;

        var fromPattern = java.util.regex.Pattern.compile("^FROM\\s+(\\S+)", java.util.regex.Pattern.MULTILINE);
        var fromMatcher = fromPattern.matcher(content);
        while (fromMatcher.find()) {
            String image = fromMatcher.group(1);
            entries.add(new TechEntry("Docker Base Image", image, "인프라", "https://hub.docker.com/_/" + image.split(":")[0].split("/")[image.split("/").length > 1 ? 1 : 0], "Dockerfile"));
        }
    }

    private DocMapping findDocMapping(String name) {
        String lower = name.toLowerCase();
        // 정확한 매칭
        if (DOCS_MAP.containsKey(lower)) return DOCS_MAP.get(lower);
        // 부분 매칭 (spring-ai-starter-model-openai → spring-ai)
        for (Map.Entry<String, DocMapping> entry : DOCS_MAP.entrySet()) {
            if (lower.contains(entry.getKey())) return entry.getValue();
        }
        return null;
    }

    private String formatResult(String owner, String repo, List<TechEntry> entries) {
        // 중복 제거 (같은 displayName)
        Map<String, TechEntry> unique = new LinkedHashMap<>();
        for (TechEntry entry : entries) {
            String key = entry.name;
            if (!unique.containsKey(key) || (!entry.version.isEmpty() && unique.get(key).version.isEmpty())) {
                unique.put(key, entry);
            }
        }

        StringBuilder sb = new StringBuilder();
        sb.append(String.format("## %s/%s 기술 스택 분석\n\n", owner, repo));

        // 카테고리별 그룹핑
        Map<String, List<TechEntry>> grouped = new LinkedHashMap<>();
        for (TechEntry entry : unique.values()) {
            grouped.computeIfAbsent(entry.category, k -> new ArrayList<>()).add(entry);
        }

        String[] categoryOrder = {"언어", "프레임워크", "라이브러리", "빌드 도구", "데이터베이스", "인프라"};
        for (String category : categoryOrder) {
            List<TechEntry> items = grouped.get(category);
            if (items == null || items.isEmpty()) continue;

            sb.append(String.format("### %s\n", category));
            for (TechEntry item : items) {
                String versionStr = item.version.isEmpty() ? "" : " " + item.version;
                sb.append(String.format("- **%s%s** — %s (출처: %s)\n", item.name, versionStr, item.docsUrl, item.source));
            }
            sb.append("\n");
        }

        sb.append(String.format("총 %d개 기술 감지됨\n", unique.size()));
        return sb.toString();
    }

    private record DocMapping(String displayName, String category, String docsUrl) {}
    private record TechEntry(String name, String version, String category, String docsUrl, String source) {}
}
