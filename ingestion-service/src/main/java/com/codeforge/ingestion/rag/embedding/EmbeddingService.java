package com.codeforge.ingestion.rag.embedding;

import com.codeforge.ingestion.entity.CodeFile;
import com.codeforge.ingestion.entity.CodeRepository;
import com.codeforge.ingestion.rag.chunk.CodeChunk;
import com.codeforge.ingestion.rag.chunk.ChunkingService;
import com.codeforge.ingestion.repository.CodeFileRepository;
import com.codeforge.ingestion.repository.CodeRepositoryRepository;
import com.codeforge.ingestion.service.MinioService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Recover;
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
    private final CodeRepositoryRepository codeRepositoryRepository;
    private final RestTemplate restTemplate;
    private final MinioService minioService;

    @Value("${minio.url:http://localhost:9000}")
    private String minioUrl;

    @Value("${minio.access-key:minioadmin}")
    private String minioAccessKey;

    @Value("${minio.secret-key:minioadmin123}")
    private String minioSecretKey;

    @Value("${minio.bucket:codeforge-repos}")
    private String minioBucket;

//    // Ollama embedding config
//    private static final String OLLAMA_API_URL =
//            "http://localhost:11434/api/embeddings";
//    private static final String OLLAMA_MODEL = "nomic-embed-text";
//
//    private static final String GEMINI_API_URL =
//            "https://generativelanguage.googleapis.com/v1beta/models/gemini-embedding-001:embedContent";
//
//    @Value("${gemini.api-key:}")
//    private String geminiApiKey;

    private static final String NVIDIA_API_URL =
            "https://integrate.api.nvidia.com/v1/embeddings";
    private static final String NVIDIA_MODEL =
            "nvidia/nv-embedcode-7b-v1";

    @Value("${nvidia.api-key:}")
    private String nvidiaApiKey;


    @Async
    public void embedRepository(String repositoryId) {
        log.info("Starting embedding pipeline for repository: {}",
                repositoryId);

        // ── Pre-flight: verify ingestion completed successfully ──────────
        CodeRepository repo = codeRepositoryRepository.findById(repositoryId)
                .orElse(null);
        if (repo == null) {
            log.error("Embedding aborted — repository not found in DB: {}. "
                    + "Make sure you ran POST /api/ingestion/ingest first.",
                    repositoryId);
            return;
        }
        if (repo.getStatus() != CodeRepository.IngestionStatus.COMPLETED) {
            log.error("Embedding aborted — repository {} has status '{}', expected COMPLETED. "
                    + "Wait for ingestion to finish (or check for ingestion errors) before embedding.",
                    repositoryId, repo.getStatus());
            return;
        }

        List<CodeFile> files = codeFileRepository
                .findByRepositoryId(repositoryId);

        if (files.isEmpty()) {
            log.error("Embedding aborted — no files found in DB for repository: {}. "
                    + "Ingestion status is COMPLETED but code_files table has 0 rows. "
                    + "Check MinIO bucket 'codeforge-repos' and re-run ingestion.",
                    repositoryId);
            return;
        }
        log.info("Found {} files to embed for repository: {}", files.size(), repositoryId);

        int embedded = 0;
        int skipped = 0;
        int totalChunks = 0;
        int failedChunks = 0;

        for (CodeFile file : files) {
            try {
                if (shouldSkipEmbedding(file.getLanguage())) {
                    skipped++;
                    continue;
                }

                String content = readFileFromMinio(
                        file.getMinioPath());
                if (content == null || content.isBlank()) {
                    log.warn("Skipping file {} — empty or unreadable from MinIO",
                            file.getFileName());
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

                int storedInFile = 0;
                for (CodeChunk chunk : chunks) {
                    totalChunks++;
                    // This now calls the retryable method
                    List<Float> embedding =
                            getEmbedding(chunk.getContent());

                    if (embedding != null) {
                        qdrantService.storeChunk(chunk, embedding);
                        storedInFile++;
                    } else {
                        failedChunks++;
                        log.warn("NVIDIA returned null embedding for chunk {} of file {}",
                                totalChunks, file.getFileName());
                    }
                    try {
                        Thread.sleep(1500);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                }

                embedded++;
                log.info("[{}/{}] Embedded file: {} ({} chunks, {} stored)",
                        embedded + skipped, files.size(),
                        file.getFileName(), chunks.size(), storedInFile);

            } catch (Exception e) {
                log.warn("Failed to embed file {}: {}",
                        file.getFileName(), e.getMessage());
            }
        }

        log.info("Embedding complete for repository: {}. " +
                        "Files — Embedded: {}, Skipped: {}. " +
                        "Chunks — Total: {}, Failed: {}",
                repositoryId, embedded, skipped,
                totalChunks, failedChunks);
    }

    // Retries up to 4 times for RestClient exceptions (Timeouts, 429s, 500s).
    // Uses randomized delay (Jitter) to prevent thundering herd: ~2s, ~4s, ~8s
    @Retryable(
            value = {RestClientException.class},
            maxAttempts = 4,
            backoff = @Backoff(delay = 2000, multiplier = 2.0, random = true)
    )
    public List<Float> getEmbedding(String text) {
        return callNvidiaEmbedding(text, "passage");
    }

    public List<Float> getQueryEmbedding(String text) {
        return callNvidiaEmbedding(text, "query");
    }

    private List<Float> callNvidiaEmbedding(String text,
                                            String inputType) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(nvidiaApiKey);

        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("input", text);
        requestBody.put("model", NVIDIA_MODEL);
        requestBody.put("input_type", inputType);
        requestBody.put("encoding_format", "float");
        requestBody.put("truncate", "END");

        HttpEntity<Map<String, Object>> request =
                new HttpEntity<>(requestBody, headers);

        // Let RestClientException propagate so @Retryable can retry
        // on 502, 429, 500, timeouts, etc.
        ResponseEntity<Map> response = restTemplate.postForEntity(
                NVIDIA_API_URL, request, Map.class);

        if (response.getStatusCode() == HttpStatus.OK
                && response.getBody() != null) {
            List<Map<String, Object>> data =
                    (List<Map<String, Object>>)
                            response.getBody().get("data");
            if (data != null && !data.isEmpty()) {
                List<Double> embedding =
                        (List<Double>) data.get(0).get("embedding");
                return embedding.stream()
                        .map(Double::floatValue)
                        .collect(Collectors.toList());
            }
        }
        log.warn("NVIDIA returned OK but no embedding data for input_type={}",
                inputType);
        return null;
    }

    // Fallback if all 4 retries fail. Keeps the pipeline from crashing.
    @Recover
    public List<Float> recoverFromEmbeddingFailure(
            RestClientException e, String text) {
        log.error("Exhausted all retries for NVIDIA embedding. " +
                "Skipping chunk. Error: {}", e.getMessage());
        return null;
    }

    private String readFileFromMinio(String minioPath) {
        return minioService.readFile(minioPath);
    }

    private boolean shouldSkipEmbedding(String language) {
        return "Unknown".equals(language);
    }
}