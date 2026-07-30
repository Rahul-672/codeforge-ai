package com.codeforge.ingestion.rag.search;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RAGResponse implements Serializable {

    // The actual answer from LLM (or from search results)
    private String answer;

    // Citations — which files/methods were used
    private List<Citation> citations;

    // Evaluation scores
    private EvaluationResult evaluation;

    // Raw search results for debugging
    private List<SearchResult> retrievedChunks;

    // Was reranking applied?
    private boolean reranked;

    // Total chunks retrieved before reranking
    private int totalCandidates;

    // ── Performance Timing Fields (for BEFORE vs AFTER benchmarking) ──
    private long pipelineTimeMs;     // Total end-to-end pipeline time
    private long embeddingTimeMs;    // NVIDIA embedding API call time
    private long qdrantTimeMs;       // Qdrant vector search time
    private long rerankTimeMs;       // Reranking time
    private long evalTimeMs;         // RAG evaluation time
    private boolean cachedResult;    // true if served from Redis cache
}