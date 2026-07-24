package com.codeforge.ingestion.rag.embedding;

import com.codeforge.ingestion.entity.CodeFile;
import com.codeforge.ingestion.rag.chunk.CodeChunk;
import com.codeforge.ingestion.rag.chunk.ChunkingService;
import com.codeforge.ingestion.repository.CodeFileRepository;
import io.minio.GetObjectArgs;
import io.minio.MinioClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class EmbeddingService {

    private final ChunkingService chunkingService;
    private final QdrantService qdrantService;
    private final CodeFileRepository codeFileRepository;
    private final RestTemplate restTemplate;

    @Value("${minio.url}")
    private String minioUrl;

    @Value("${minio.access-key}")
    private String minioAccessKey;

    @Value("${minio.secret-key}")
    private String minioSecretKey;

    @Value("${minio.bucket}")
    private String minioBucket;

    @Value("${gemini.api-key:}")
    private String geminiApiKey;

    private static final String GEMINI_BATCH_API_URL =
            "https://generativelanguage.googleapis.com/v1beta/models/gemini-embedding-001:batchEmbedContents";

    // Process up to 50 chunks in a single HTTP request
    private static final int BATCH_SIZE = 50;

    @Async
    public void embedRepository(String repositoryId) {
        log.info("Starting embedding pipeline for repository: {}", repositoryId);

        List<CodeFile> files = codeFileRepository.findByRepositoryId(repositoryId);
        int embedded = 0;
        int skipped = 0;

        for (CodeFile file : files) {
            try {
                if (shouldSkipEmbedding(file.getLanguage())) {
                    skipped++;
                    continue;
                }

                String content = readFileFromMinio(file.getMinioPath());
                if (content == null || content.isBlank()) {
                    skipped++;
                    continue;
                }

                List<CodeChunk> chunks = chunkingService.chunkFile(
                        content, repositoryId, file.getId(),
                        file.getFilePath(), file.getFileName(), file.getLanguage());

                // Process chunks in batches rather than individually
                for (int i = 0; i < chunks.size(); i += BATCH_SIZE) {
                    List<CodeChunk> batchChunks = chunks.subList(i, Math.min(i + BATCH_SIZE, chunks.size()));
                    processBatch(batchChunks);

                    // Respect free-tier rate limits (sleep between batches, not chunks)
                    try {
                        Thread.sleep(4000);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                }

                embedded++;
                log.debug("Embedded file: {} ({} chunks)", file.getFileName(), chunks.size());

            } catch (Exception e) {
                log.warn("Failed to embed file {}: {}", file.getFileName(), e.getMessage());
            }
        }

        log.info("Embedding complete for repository: {}. Embedded: {}, Skipped: {}",
                repositoryId, embedded, skipped);
    }

    // Automatically retry 3 times if the connection is reset
    @Retryable(
            value = {RestClientException.class},
            maxAttempts = 3,
            backoff = @Backoff(delay = 2000, multiplier = 2)
    )
    public void processBatch(List<CodeChunk> batchChunks) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        List<Map<String, Object>> requests = new ArrayList<>();

        // Construct the correct batch request array for Gemini
        for (CodeChunk chunk : batchChunks) {
            Map<String, Object> content = new HashMap<>();
            content.put("parts", List.of(Map.of("text", chunk.getContent())));

            Map<String, Object> request = new HashMap<>();
            request.put("model", "models/gemini-embedding-001");
            request.put("content", content);
            request.put("taskType", "RETRIEVAL_DOCUMENT");

            requests.add(request);
        }

        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("requests", requests);

        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);
        String url = GEMINI_BATCH_API_URL + "?key=" + geminiApiKey;

        ResponseEntity<Map> response = restTemplate.postForEntity(url, entity, Map.class);

        if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
            List<Map<String, Object>> embeddingsList =
                    (List<Map<String, Object>>) response.getBody().get("embeddings");

            if (embeddingsList != null && embeddingsList.size() == batchChunks.size()) {

                // 1. Gather all vectors for this batch
                List<List<Float>> batchVectors = new ArrayList<>();
                for (int i = 0; i < batchChunks.size(); i++) {
                    List<Double> values = (List<Double>) embeddingsList.get(i).get("values");
                    List<Float> floatValues = values.stream()
                            .map(Double::floatValue)
                            .collect(Collectors.toList());
                    batchVectors.add(floatValues);
                }

                // 2. Send the entire array of chunks and vectors to Qdrant at once
                qdrantService.storeBatch(batchChunks, batchVectors);
            }
        } else {
            throw new RestClientException("Failed to get embeddings from Gemini. Status code: " + response.getStatusCode());
        }
    }

    public List<Float> getEmbedding(String text) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        Map<String, Object> content = new HashMap<>();
        content.put("parts", List.of(Map.of("text", text)));

        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("model", "models/gemini-embedding-001");
        requestBody.put("content", content);

        // Optimize the embedding for user search queries rather than document storage
        requestBody.put("taskType", "RETRIEVAL_QUERY");

        HttpEntity<Map<String, Object>> request = new HttpEntity<>(requestBody, headers);
        String url = "https://generativelanguage.googleapis.com/v1beta/models/gemini-embedding-001:embedContent?key=" + geminiApiKey;

        try {
            ResponseEntity<Map> response = restTemplate.postForEntity(url, request, Map.class);

            if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
                Map<String, Object> embedding = (Map<String, Object>) response.getBody().get("embedding");
                if (embedding != null) {
                    List<Double> values = (List<Double>) embedding.get("values");
                    return values.stream()
                            .map(Double::floatValue)
                            .collect(Collectors.toList());
                }
            }
        } catch (Exception e) {
            log.error("Error getting single embedding for search query: {}", e.getMessage());
        }
        return null;
    }

    private String readFileFromMinio(String minioPath) {
        try {
            MinioClient minioClient = MinioClient.builder()
                    .endpoint(minioUrl)
                    .credentials(minioAccessKey, minioSecretKey)
                    .build();

            var stream = minioClient.getObject(
                    GetObjectArgs.builder()
                            .bucket(minioBucket)
                            .object(minioPath)
                            .build());

            return new BufferedReader(
                    new InputStreamReader(
                            stream, StandardCharsets.UTF_8))
                    .lines()
                    .collect(Collectors.joining("\n"));

        } catch (Exception e) {
            log.warn("Failed to read file from MinIO {}: {}",
                    minioPath, e.getMessage());
            return null;
        }
    }

    private boolean shouldSkipEmbedding(String language) {
        return "Unknown".equals(language);
    }
}