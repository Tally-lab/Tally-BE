dependencies {
    // Spring Boot
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-webflux")
    implementation("org.springframework.boot:spring-boot-starter-validation")

    // Spring AI - Claude (Anthropic)
    implementation("org.springframework.ai:spring-ai-starter-model-anthropic")

    // Spring AI - MCP Client
    implementation("org.springframework.ai:spring-ai-starter-mcp-client")

    // Jackson
    implementation("com.fasterxml.jackson.core:jackson-databind")
    implementation("com.fasterxml.jackson.datatype:jackson-datatype-jsr310")
}
