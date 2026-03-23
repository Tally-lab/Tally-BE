dependencies {
    // Spring Boot
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-webflux")
    implementation("org.springframework.boot:spring-boot-starter-validation")

    // Spring AI - OpenAI
    implementation("org.springframework.ai:spring-ai-starter-model-openai")

    // Spring AI - MCP Client
    implementation("org.springframework.ai:spring-ai-starter-mcp-client")

    // Spring AI - Vector Store + RAG Advisor
    implementation("org.springframework.ai:spring-ai-vector-store")
    implementation("org.springframework.ai:spring-ai-advisors-vector-store")

    // Jackson
    implementation("com.fasterxml.jackson.core:jackson-databind")
    implementation("com.fasterxml.jackson.datatype:jackson-datatype-jsr310")

    // Playwright - PDF generation
    implementation("com.microsoft.playwright:playwright:1.49.0")
}
