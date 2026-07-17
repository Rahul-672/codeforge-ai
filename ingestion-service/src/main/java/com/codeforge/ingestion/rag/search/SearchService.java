package com.codeforge.ingestion.rag.search;

import com.codeforge.ingestion.rag.embedding.QdrantService;
import io.qdrant.client.grpc.Points.ScoredPoint;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import com.codeforge.ingestion.rag.embedding.EmbeddingService;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

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

    public RAGResponse search(String query,
                              String repositoryId) {

        log.info("RAG search: '{}' in repo: {}", query, repositoryId);

        // Step 1 — embed the query
        List<Float> queryEmbedding = embeddingService
                .getEmbedding(query);
        if (queryEmbedding == null) {
            return RAGResponse.builder()
                    .answer("Failed to process query")
                    .build();
        }

        // Step 2 — retrieve top 10 candidates from Qdrant
        List<ScoredPoint> rawResults = qdrantService.search(
                queryEmbedding, repositoryId,
                INITIAL_RETRIEVAL_SIZE);

        List<SearchResult> candidates = new ArrayList<>();
        for (ScoredPoint point : rawResults) {
            candidates.add(toSearchResult(point));
        }

        log.info("Retrieved {} candidates from Qdrant",
                candidates.size());

        // Step 3 — rerank candidates, keep top 3
        List<SearchResult> reranked = rerankerService.rerank(
                query, candidates, FINAL_CHUNK_COUNT);

        log.info("Reranked to {} final chunks", reranked.size());

        // Step 4 — build prompt with citations
        String promptWithCitations = citationBuilder
                .buildPromptWithCitations(query, reranked);

        // Step 5 — build citation list
        List<Citation> citations = citationBuilder
                .buildCitations(reranked);

        // Step 6 — generate answer
        // For now return the prompt context as answer
        // Will be replaced with LLM call in agent phase
        String answer = buildContextAnswer(reranked, query);

        // Step 7 — evaluate RAG quality
        EvaluationResult evaluation = ragEvaluator.evaluate(
                query, reranked, answer);

        return RAGResponse.builder()
                .answer(answer)
                .citations(citations)
                .evaluation(evaluation)
                .retrievedChunks(reranked)
                .reranked(true)
                .totalCandidates(candidates.size())
                .build();
    }

    // Temporary answer builder until LLM is integrated
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

    private SearchResult toSearchResult(ScoredPoint point) {
        var payload = point.getPayloadMap();
        return SearchResult.builder()
                .content(getStringValue(payload, "content"))
                .filePath(getStringValue(payload, "file_path"))
                .fileName(getStringValue(payload, "file_name"))
                .language(getStringValue(payload, "language"))
                .methodName(getStringValue(payload, "method_name"))
                .score(point.getScore())
                .chunkIndex((int) getIntValue(
                        payload, "chunk_index"))
                .build();
    }

    private String getStringValue(
            java.util.Map<String,
                    io.qdrant.client.grpc.JsonWithInt.Value> payload,
            String key) {
        var value = payload.get(key);
        return value != null ? value.getStringValue() : "";
    }

    private long getIntValue(
            java.util.Map<String,
                    io.qdrant.client.grpc.JsonWithInt.Value> payload,
            String key) {
        var value = payload.get(key);
        return value != null ? value.getIntegerValue() : 0;
    }
}