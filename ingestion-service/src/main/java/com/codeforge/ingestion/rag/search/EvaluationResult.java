package com.codeforge.ingestion.rag.search;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EvaluationResult {

    // Score 0-1: Are retrieved chunks relevant to query?
    private float contextRelevance;

    // Score 0-1: Is answer grounded in retrieved chunks?
    private float answerFaithfulness;

    // Score 0-1: Does answer address the question?
    private float answerRelevance;

    // Overall RAG quality score
    private float overallScore;

    // Quality label based on score
    private String qualityLabel;

    // Any issues detected
    private String issues;
}