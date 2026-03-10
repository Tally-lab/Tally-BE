dependencies {
    // Spring Boot
    implementation("org.springframework.boot:spring-boot-starter-web")

    // Spring AI - MCP Server (WebMVC transport with SSE)
    implementation("org.springframework.ai:spring-ai-starter-mcp-server-webmvc")

    // Kubernetes Java Client
    implementation("io.kubernetes:client-java:21.0.1")
}
