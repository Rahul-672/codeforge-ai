package com.codeforge.ingestion.rag.embedding;

import com.codeforge.ingestion.rag.chunk.CodeChunk;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import jakarta.annotation.PostConstruct;
import java.util.*;

@Slf4j
@Service
public class QdrantService {

    private static final String COLLECTION_NAME = "codeforge_chunks";

    // Changed from 3072 to 768 to match gemini-embedding-001 output
    private static final int VECTOR_SIZE = 3072;

    private final RestTemplate restTemplate;

    @Value("${qdrant.url:}")
    private String qdrantUrl;

    @Value("${qdrant.api-key:}")
    private String qdrantApiKey;

    public QdrantService(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    @PostConstruct
    public void init() {
        try {
            createCollectionIfNotExists();
            log.info("Qdrant REST client initialized: {}", qdrantUrl);
        } catch (Exception e) {
            log.error("Failed to initialize Qdrant: {}", e.getMessage());
        }
    }

    private HttpHeaders getHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("api-key", qdrantApiKey);
        return headers;
    }

    private void createCollectionIfNotExists() {
        try {
            String url = qdrantUrl + "/collections/" + COLLECTION_NAME;
            HttpEntity<Void> request = new HttpEntity<>(getHeaders());

            try {
                restTemplate.exchange(url, HttpMethod.GET, request, Map.class);
                log.info("Qdrant collection already exists: {}", COLLECTION_NAME);
            } catch (Exception e) {
                Map<String, Object> body = new HashMap<>();
                Map<String, Object> vectors = new HashMap<>();
                vectors.put("size", VECTOR_SIZE);
                vectors.put("distance", "Cosine");
                body.put("vectors", vectors);

                HttpEntity<Map<String, Object>> createRequest =
                        new HttpEntity<>(body, getHeaders());

                restTemplate.exchange(url, HttpMethod.PUT, createRequest, Map.class);
                log.info("Created Qdrant collection: {}", COLLECTION_NAME);
            }
        } catch (Exception e) {
            log.error("Error creating Qdrant collection: {}", e.getMessage());
        }
    }

    // NEW METHOD: Accepts a batch of chunks and embeddings simultaneously
    public void storeBatch(List<CodeChunk> chunks, List<List<Float>> embeddings) {
        if (chunks.isEmpty() || embeddings.isEmpty() || chunks.size() != embeddings.size()) {
            return;
        }

        try {
            String url = qdrantUrl + "/collections/" + COLLECTION_NAME + "/points";
            List<Map<String, Object>> points = new ArrayList<>();

            for (int i = 0; i < chunks.size(); i++) {
                CodeChunk chunk = chunks.get(i);

                Map<String, Object> point = new HashMap<>();
                point.put("id", chunk.getId());
                point.put("vector", embeddings.get(i));

                Map<String, Object> payload = new HashMap<>();
                payload.put("content", chunk.getContent());
                payload.put("repository_id", chunk.getRepositoryId());
                payload.put("file_path", chunk.getFilePath());
                payload.put("file_name", chunk.getFileName());
                payload.put("language", chunk.getLanguage());
                payload.put("method_name", chunk.getMethodName() != null ? chunk.getMethodName() : "");
                payload.put("chunk_index", chunk.getChunkIndex());
                payload.put("chunk_type", chunk.getChunkType());

                point.put("payload", payload);
                points.add(point);
            }

            Map<String, Object> body = new HashMap<>();
            body.put("points", points);

            HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, getHeaders());

            // Sending 50 points in a single HTTP request eliminates the 502 Gateway overload
            restTemplate.exchange(url + "?wait=true", HttpMethod.PUT, request, Map.class);

        } catch (Exception e) {
            log.error("Error storing chunk batch in Qdrant: {}", e.getMessage());
        }
    }

    public List<Map<String, Object>> search(List<Float> queryVector, String repositoryId, int topK) {
        // ... (Keep your existing search method here, no changes needed) ...
        try {
            String url = qdrantUrl + "/collections/" + COLLECTION_NAME + "/points/search";

            Map<String, Object> filter = new HashMap<>();
            Map<String, Object> must = new HashMap<>();
            Map<String, Object> fieldCondition = new HashMap<>();
            Map<String, Object> matchCondition = new HashMap<>();
            matchCondition.put("value", repositoryId);
            fieldCondition.put("key", "repository_id");
            fieldCondition.put("match", matchCondition);
            must.put("field", fieldCondition);
            filter.put("must", List.of(must));

            Map<String, Object> body = new HashMap<>();
            body.put("vector", queryVector);
            body.put("limit", topK);
            body.put("filter", filter);
            body.put("with_payload", true);

            HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, getHeaders());

            ResponseEntity<Map> response = restTemplate.exchange(url, HttpMethod.POST, request, Map.class);

            if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
                return (List<Map<String, Object>>) response.getBody().get("result");
            }
        } catch (Exception e) {
            log.error("Error searching Qdrant: {}", e.getMessage());
        }
        return List.of();
    }

    public void deleteByRepositoryId(String repositoryId) {
        // ... (Keep your existing deleteByRepositoryId method here, no changes needed) ...
        try {
            String url = qdrantUrl + "/collections/" + COLLECTION_NAME + "/points/delete";

            Map<String, Object> filter = new HashMap<>();
            Map<String, Object> must = new HashMap<>();
            Map<String, Object> fieldCondition = new HashMap<>();
            Map<String, Object> matchCondition = new HashMap<>();
            matchCondition.put("value", repositoryId);
            fieldCondition.put("key", "repository_id");
            fieldCondition.put("match", matchCondition);
            must.put("field", fieldCondition);
            filter.put("must", List.of(must));

            Map<String, Object> body = new HashMap<>();
            body.put("filter", filter);

            HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, getHeaders());

            restTemplate.exchange(url, HttpMethod.POST, request, Map.class);
            log.info("Deleted vectors for repository: {}", repositoryId);
        } catch (Exception e) {
            log.error("Error deleting from Qdrant: {}", e.getMessage());
        }
    }
}