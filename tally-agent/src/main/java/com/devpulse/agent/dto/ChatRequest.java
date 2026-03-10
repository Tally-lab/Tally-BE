package com.devpulse.agent.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class ChatRequest {

    @NotBlank(message = "Message is required")
    private String message;

    private String githubToken;

    private String conversationId;

    private String selectedOrg;
}
