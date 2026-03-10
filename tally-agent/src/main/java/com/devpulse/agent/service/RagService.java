package com.devpulse.agent.service;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class RagService {

    private final VectorStore vectorStore;

    private final TokenTextSplitter textSplitter = TokenTextSplitter.builder()
            .withChunkSize(800)
            .withMinChunkSizeChars(350)
            .withMinChunkLengthToEmbed(5)
            .withMaxNumChunks(100)
            .build();

    @PostConstruct
    public void loadDefaultDocuments() {
        try {
            PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
            Resource[] resources = resolver.getResources("classpath:rag/*.md");

            List<Document> allChunks = new ArrayList<>();
            for (Resource resource : resources) {
                String content = resource.getContentAsString(StandardCharsets.UTF_8);
                String filename = resource.getFilename();

                List<Document> docs = List.of(new Document(content, Map.of(
                        "source", filename != null ? filename : "unknown",
                        "type", "industry-standard"
                )));

                List<Document> chunks = textSplitter.apply(docs);
                allChunks.addAll(chunks);
                log.info("Loaded RAG document: {} → {} chunks", filename, chunks.size());
            }

            if (!allChunks.isEmpty()) {
                vectorStore.add(allChunks);
                log.info("Total RAG documents loaded: {} chunks from {} files", allChunks.size(), resources.length);
            }
        } catch (IOException e) {
            log.warn("Failed to load default RAG documents: {}", e.getMessage());
        }
    }

    public void addDocument(String title, String content) {
        List<Document> docs = List.of(new Document(content, Map.of(
                "source", title,
                "type", "team-document"
        )));

        List<Document> chunks = textSplitter.apply(docs);
        vectorStore.add(chunks);
        log.info("Added team document: {} → {} chunks", title, chunks.size());
    }

    public List<Document> search(String query, int topK) {
        return vectorStore.similaritySearch(
                SearchRequest.builder()
                        .query(query)
                        .topK(topK)
                        .build()
        );
    }
}
