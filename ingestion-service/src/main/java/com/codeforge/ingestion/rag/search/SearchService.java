package com.codeforge.ingestion.rag.search;

import com.codeforge.ingestion.rag.embedding.EmbeddingService;
import com.codeforge.ingestion.rag.embedding.QdrantService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class SearchService {

    private final QdrantService qdrantService;
    private final EmbeddingService embeddingService;
    private final RerankerService rerankerService;
    private final CitationBuilder citationBuilder;
    private final RAGEvaluator ragEvaluator;

    private static final int INITIAL_RETRIEVAL_SIZE = 10;
    private static final int FINAL_CHUNK_COUNT = 3;

    /**
     * Cached RAG search — returns from Redis on cache hit.
     * Uses Spring @Cacheable with composite key: repositoryId + query.
     */
    @Cacheable(value = "rag_search", key = "#repositoryId + ':' + #query")
    public RAGResponse search(String query, String repositoryId) {
        return executeSearch(query, repositoryId);
    }

    // ── TEST — BENCHMARK ONLY ──────────────────────────────────────
    // This method bypasses Redis cache entirely so benchmark scripts
    // can measure raw pipeline latency (BEFORE optimization baseline).
    // Not called in production — only via ?bypassCache=true parameter.
    // ────────────────────────────────────────────────────────────────
    public RAGResponse searchNoCache(String query, String repositoryId) {
        return executeSearch(query, repositoryId);
    }
    // ── END TEST ───────────────────────────────────────────────────

    /**
     * Core RAG search pipeline — shared by both cached and uncached paths.
     * Measures and logs timing for each pipeline stage.
     */
    private RAGResponse executeSearch(String query, String repositoryId) {
        log.info("RAG search: '{}' in repo: {}", query, repositoryId);

        long pipelineStart = System.currentTimeMillis();

        // Step 1 — embed query
        long stepStart = System.currentTimeMillis();
        List<Float> queryEmbedding =
                embeddingService.getQueryEmbedding(query);
        long embeddingTime = System.currentTimeMillis() - stepStart;

        if (queryEmbedding == null) {
            return RAGResponse.builder()
                    .answer("Failed to process query")
                    .build();
        }

        // Step 2 — retrieve from Qdrant
        stepStart = System.currentTimeMillis();
        List<Map<String, Object>> rawResults = qdrantService.search(
                queryEmbedding, repositoryId, INITIAL_RETRIEVAL_SIZE);
        long qdrantTime = System.currentTimeMillis() - stepStart;

        List<SearchResult> candidates = new ArrayList<>();
        for (Map<String, Object> point : rawResults) {
            candidates.add(toSearchResult(point));
        }

        log.info("Retrieved {} candidates from Qdrant",
                candidates.size());

        // Step 3 — rerank
        stepStart = System.currentTimeMillis();
        List<SearchResult> reranked = rerankerService.rerank(
                query, candidates, FINAL_CHUNK_COUNT);
        long rerankTime = System.currentTimeMillis() - stepStart;

        log.info("Reranked to {} final chunks", reranked.size());

        // Step 4 — build citations
        String promptWithCitations = citationBuilder
                .buildPromptWithCitations(query, reranked);
        List<Citation> citations = citationBuilder
                .buildCitations(reranked);

        // Step 5 — build answer
        String answer = buildContextAnswer(reranked, query);

        // Step 6 — evaluate
        stepStart = System.currentTimeMillis();
        EvaluationResult evaluation = ragEvaluator.evaluate(
                query, reranked, answer);
        long evalTime = System.currentTimeMillis() - stepStart;

        long totalTime = System.currentTimeMillis() - pipelineStart;

        // Log per-stage timing breakdown for benchmarking
        log.info("Pipeline timing — Total: {}ms | Embedding: {}ms | Qdrant: {}ms | Rerank: {}ms | Eval: {}ms",
                totalTime, embeddingTime, qdrantTime, rerankTime, evalTime);

        return RAGResponse.builder()
                .answer(answer)
                .citations(citations)
                .evaluation(evaluation)
                .retrievedChunks(reranked)
                .reranked(true)
                .totalCandidates(candidates.size())
                .pipelineTimeMs(totalTime)           // timing field
                .embeddingTimeMs(embeddingTime)       // timing field
                .qdrantTimeMs(qdrantTime)             // timing field
                .rerankTimeMs(rerankTime)             // timing field
                .evalTimeMs(evalTime)                 // timing field
                .cachedResult(false)                  // this was a fresh computation
                .build();
    }

    private String buildContextAnswer(List<SearchResult> chunks,
                                      String query) {
        StringBuilder sb = new StringBuilder();
        sb.append("Based on the codebase, here are the most ")
                .append("relevant code sections for: '")
                .append(query).append("'\n\n");

        for (int i = 0; i < chunks.size(); i++) {
            SearchResult chunk = chunks.get(i);
            sb.append("[").append(i + 1).append("] ")
                    .append(chunk.getFileName());
            if (chunk.getMethodName() != null
                    && !chunk.getMethodName().isEmpty()) {
                sb.append(" → ").append(chunk.getMethodName());
            }
            sb.append(" (relevance: ")
                    .append(String.format("%.2f", chunk.getScore()))
                    .append(")\n");
        }
        return sb.toString();
    }

    private SearchResult toSearchResult(Map<String, Object> point) {
        Map<String, Object> payload =
                (Map<String, Object>) point.get("payload");
        float score = ((Number) point.get("score")).floatValue();

        return SearchResult.builder()
                .content(getString(payload, "content"))
                .filePath(getString(payload, "file_path"))
                .fileName(getString(payload, "file_name"))
                .language(getString(payload, "language"))
                .methodName(getString(payload, "method_name"))
                .score(score)
                .chunkIndex(getInt(payload, "chunk_index"))
                .build();
    }

    private String getString(Map<String, Object> map, String key) {
        Object val = map.get(key);
        return val != null ? val.toString() : "";
    }

    private int getInt(Map<String, Object> map, String key) {
        Object val = map.get(key);
        return val != null ? ((Number) val).intValue() : 0;
    }
}