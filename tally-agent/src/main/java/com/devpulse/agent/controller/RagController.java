package com.devpulse.agent.controller;

import com.devpulse.agent.service.RagService;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.document.Document;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/rag")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class RagController {

    private final RagService ragService;

    @PostMapping("/documents")
    public Map<String, String> addDocument(@RequestBody Map<String, String> request) {
        String title = request.get("title");
        String content = request.get("content");
        ragService.addDocument(title, content);
        return Map.of("status", "ok", "title", title);
    }

    @PostMapping("/search")
    public List<Map<String, Object>> search(@RequestBody Map<String, Object> request) {
        String query = (String) request.get("query");
        int topK = request.get("topK") != null ? (int) request.get("topK") : 5;

        return ragService.search(query, topK).stream()
                .map(doc -> Map.<String, Object>of(
                        "content", doc.getText(),
                        "metadata", doc.getMetadata(),
                        "score", doc.getScore() != null ? doc.getScore() : 0.0
                ))
                .toList();
    }
}
