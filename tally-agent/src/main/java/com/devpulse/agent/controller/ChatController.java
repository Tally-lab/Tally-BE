package com.devpulse.agent.controller;

import com.devpulse.agent.service.AgentService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;

import java.util.Map;

@RestController
@RequestMapping("/api/chat")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class ChatController {

    private final AgentService agentService;

    @PostMapping
    public Map<String, String> chat(@RequestBody Map<String, String> request) {
        String message = request.get("message");
        String githubToken = request.get("githubToken");
        String response = agentService.chat(message, githubToken);
        return Map.of("response", response);
    }

    @PostMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> chatStream(@RequestBody Map<String, String> request) {
        String message = request.get("message");
        String githubToken = request.get("githubToken");
        return agentService.chatStream(message, githubToken);
    }
}
