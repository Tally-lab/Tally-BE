package com.devpulse.agent.controller;

import com.devpulse.agent.dto.ChatRequest;
import com.devpulse.agent.dto.ChatResponse;
import com.devpulse.agent.service.AgentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class ChatController {

    private final AgentService agentService;

    @PostMapping("/user/organizations")
    public List<String> getUserOrganizations(@RequestBody Map<String, String> body) {
        String githubToken = body.get("githubToken");
        return agentService.getUserOrganizations(githubToken);
    }

    @PostMapping("/chat")
    public ChatResponse chat(@Valid @RequestBody ChatRequest request) {
        String conversationId = resolveConversationId(request.getConversationId());

        String response = agentService.chat(
                request.getMessage(),
                request.getGithubToken(),
                conversationId,
                request.getSelectedOrg()
        );

        return ChatResponse.builder()
                .response(response)
                .conversationId(conversationId)
                .build();
    }

    @PostMapping(value = "/chat/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> chatStream(@RequestBody ChatRequest request) {
        String conversationId = resolveConversationId(request.getConversationId());

        return agentService.chatStream(
                request.getMessage(),
                request.getGithubToken(),
                conversationId,
                request.getSelectedOrg()
        );
    }

    @DeleteMapping("/chat/conversations/{conversationId}")
    public void clearConversation(@PathVariable String conversationId) {
        agentService.clearConversation(conversationId);
    }

    private String resolveConversationId(String conversationId) {
        return (conversationId != null && !conversationId.isBlank())
                ? conversationId
                : UUID.randomUUID().toString();
    }
}
