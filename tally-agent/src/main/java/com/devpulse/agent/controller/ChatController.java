package com.devpulse.agent.controller;

import com.devpulse.agent.dto.ChatRequest;
import com.devpulse.agent.dto.ChatResponse;
import com.devpulse.agent.service.AgentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;

import java.util.UUID;

@RestController
@RequestMapping("/api/chat")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class ChatController {

    private final AgentService agentService;

    @PostMapping
    public ChatResponse chat(@Valid @RequestBody ChatRequest request) {
        String conversationId = resolveConversationId(request.getConversationId());

        String response = agentService.chat(
                request.getMessage(),
                request.getGithubToken(),
                conversationId
        );

        return ChatResponse.builder()
                .response(response)
                .conversationId(conversationId)
                .build();
    }

    @PostMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> chatStream(@RequestBody ChatRequest request) {
        String conversationId = resolveConversationId(request.getConversationId());

        return agentService.chatStream(
                request.getMessage(),
                request.getGithubToken(),
                conversationId
        );
    }

    @DeleteMapping("/conversations/{conversationId}")
    public void clearConversation(@PathVariable String conversationId) {
        agentService.clearConversation(conversationId);
    }

    private String resolveConversationId(String conversationId) {
        return (conversationId != null && !conversationId.isBlank())
                ? conversationId
                : UUID.randomUUID().toString();
    }
}
