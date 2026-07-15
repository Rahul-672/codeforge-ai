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
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
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

    private static final String OLLAMA_API_URL =
            "http://localhost:11434/api/embeddings";
    private static final String OLLAMA_MODEL = "nomic-embed-text";


    @Async
    public void embedRepository(String repositoryId) {
        log.info("Starting embedding pipeline for repository: {}", repositoryId);

        List<CodeFile> files = codeFileRepository
                .findByRepositoryId(repositoryId);

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
                        content,
                        repositoryId,
                        file.getId(),
                        file.getFilePath(),
                        file.getFileName(),
                        file.getLanguage());

                for (CodeChunk chunk : chunks) {
                    List<Float> embedding = getEmbedding(chunk.getContent());
                    if (embedding != null) {
                        qdrantService.storeChunk(chunk, embedding);
                    }
                }

                embedded++;
                log.debug("Embedded file: {} ({} chunks)",
                        file.getFileName(), chunks.size());

            } catch (Exception e) {
                log.warn("Failed to embed file {}: {}",
                        file.getFileName(), e.getMessage());
            }
        }

        log.info("Embedding complete for repository: {}. " +
                "Embedded: {}, Skipped: {}", repositoryId, embedded, skipped);
    }

    public List<Float> getEmbedding(String text) {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("model", OLLAMA_MODEL);
            requestBody.put("prompt", text);

            HttpEntity<Map<String, Object>> request =
                    new HttpEntity<>(requestBody, headers);

            ResponseEntity<Map> response = restTemplate.postForEntity(
                    OLLAMA_API_URL, request, Map.class);

            if (response.getStatusCode() == HttpStatus.OK
                    && response.getBody() != null) {
                List<Double> embeddingDoubles =
                        (List<Double>) response.getBody().get("embedding");
                if (embeddingDoubles != null) {
                    return embeddingDoubles.stream()
                            .map(Double::floatValue)
                            .collect(Collectors.toList());
                }
            }
        } catch (Exception e) {
            log.error("Error getting embedding from Ollama: {}",
                    e.getMessage());
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
                    new InputStreamReader(stream, StandardCharsets.UTF_8))
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