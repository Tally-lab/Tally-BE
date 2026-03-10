package com.devpulse.agent.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.util.UUID;

@Slf4j
@Service
public class AgentService {

    private static final String BASE_SYSTEM_PROMPT = """
            You are DevPulse, an AI engineering intelligence coach that helps
            development teams analyze GitHub repositories, diagnose team health,
            and provide actionable insights.

            ## Capabilities
            You have access to GitHub analysis tools via MCP:
            - listRepos: List user's accessible repositories
            - analyzeRepo: Analyze contributions for a specific repository
            - getCommitQuality: Analyze commit quality (Conventional Commits adherence)
            - getPrQuality: Analyze PR quality (merge rate, review time)
            - getOrgStats: Analyze organization-wide contributions
            - compareRepos: Compare multiple repositories side by side
            - listOrganizations: List user's organizations

            ## Guidelines
            - Provide data-driven insights backed by specific evidence from the tools.
            - When analyzing, start with an overview then drill into specifics.
            - Suggest actionable improvements based on metrics.
            - Compare against industry best practices when relevant.
            - If a tool call fails, explain the error and suggest alternatives.
            - Respond in Korean when the user writes in Korean.
            """;

    private final ChatClient chatClient;
    private final ChatMemory chatMemory;

    public AgentService(ChatClient.Builder chatClientBuilder, ChatMemory chatMemory) {
        this.chatMemory = chatMemory;
        this.chatClient = chatClientBuilder
                .defaultSystem(BASE_SYSTEM_PROMPT)
                .defaultAdvisors(MessageChatMemoryAdvisor.builder(chatMemory).build())
                .build();
    }

    public String chat(String message, String githubToken, String conversationId) {
        conversationId = resolveConversationId(conversationId);
        log.info("Chat request [conversationId={}]: {}", conversationId, message);

        String systemPrompt = buildSystemPrompt(githubToken);
        String cid = conversationId;

        return chatClient.prompt()
                .system(systemPrompt)
                .user(message)
                .advisors(a -> a.param(ChatMemory.CONVERSATION_ID, cid))
                .call()
                .content();
    }

    public Flux<String> chatStream(String message, String githubToken, String conversationId) {
        conversationId = resolveConversationId(conversationId);
        log.info("Stream chat request [conversationId={}]: {}", conversationId, message);

        String systemPrompt = buildSystemPrompt(githubToken);
        String cid = conversationId;

        return chatClient.prompt()
                .system(systemPrompt)
                .user(message)
                .advisors(a -> a.param(ChatMemory.CONVERSATION_ID, cid))
                .stream()
                .content();
    }

    public void clearConversation(String conversationId) {
        chatMemory.clear(conversationId);
        log.info("Cleared conversation: {}", conversationId);
    }

    String resolveConversationId(String conversationId) {
        return (conversationId != null && !conversationId.isBlank())
                ? conversationId
                : UUID.randomUUID().toString();
    }

    private String buildSystemPrompt(String githubToken) {
        if (githubToken == null || githubToken.isBlank()) {
            return BASE_SYSTEM_PROMPT;
        }
        return BASE_SYSTEM_PROMPT + """

                ## GitHub Authentication
                The user has provided a GitHub access token.
                Use this token as the 'token' parameter for ALL GitHub tool calls: %s
                Never reveal this token in your responses.
                """.formatted(githubToken);
    }
}
