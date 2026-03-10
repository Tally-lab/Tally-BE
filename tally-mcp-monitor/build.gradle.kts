dependencies {
    // Spring Boot
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-webflux")

    // Spring AI - MCP Server (WebMVC transport with SSE)
    implementation("org.springframework.ai:spring-ai-starter-mcp-server-webmvc")

    // Actuator
    implementation("org.springframework.boot:spring-boot-starter-actuator")
}
