package com.devpulse.agent.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

@Slf4j
@Service
public class AgentService {

    private final ChatClient chatClient;

    public AgentService(ChatClient.Builder chatClientBuilder) {
        this.chatClient = chatClientBuilder
                .defaultSystem("""
                    You are DevPulse, an AI engineering intelligence coach.
                    You help development teams by analyzing GitHub repositories,
                    diagnosing team health, and providing actionable insights.

                    You have access to MCP tools for GitHub analysis.
                    Always provide data-driven insights with specific evidence.
                    Respond in Korean when the user writes in Korean.
                    """)
                .build();
    }

    public String chat(String message, String githubToken) {
        log.info("Chat request: {}", message);

        return chatClient.prompt()
                .user(message)
                .call()
                .content();
    }

    public Flux<String> chatStream(String message, String githubToken) {
        log.info("Stream chat request: {}", message);

        return chatClient.prompt()
                .user(message)
                .stream()
                .content();
    }
}
