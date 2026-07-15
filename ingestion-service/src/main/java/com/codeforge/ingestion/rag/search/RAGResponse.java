package com.codeforge.ingestion.rag.search;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RAGResponse {

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
}