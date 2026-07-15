package com.codeforge.ingestion.rag.search;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Set;
import java.util.Arrays;
import java.util.stream.Collectors;

@Slf4j
@Component
public class RAGEvaluator {

    public EvaluationResult evaluate(String query,
                                     List<SearchResult> retrievedChunks,
                                     String answer) {

        float contextRelevance = evaluateContextRelevance(
                query, retrievedChunks);
        float answerFaithfulness = evaluateAnswerFaithfulness(
                answer, retrievedChunks);
        float answerRelevance = evaluateAnswerRelevance(
                query, answer);

        // Weighted overall score
        // Context relevance most important — garbage in, garbage out
        float overallScore = (0.4f * contextRelevance)
                + (0.35f * answerFaithfulness)
                + (0.25f * answerRelevance);

        String qualityLabel = getQualityLabel(overallScore);
        String issues = detectIssues(contextRelevance,
                answerFaithfulness, answerRelevance);

        log.info("RAG Evaluation — Context: {:.2f}, " +
                        "Faithfulness: {:.2f}, Relevance: {:.2f}, " +
                        "Overall: {:.2f} ({})",
                contextRelevance, answerFaithfulness,
                answerRelevance, overallScore, qualityLabel);

        return EvaluationResult.builder()
                .contextRelevance(contextRelevance)
                .answerFaithfulness(answerFaithfulness)
                .answerRelevance(answerRelevance)
                .overallScore(overallScore)
                .qualityLabel(qualityLabel)
                .issues(issues)
                .build();
    }

    // Measures: are retrieved chunks relevant to the query?
    private float evaluateContextRelevance(String query,
                                           List<SearchResult> chunks) {
        if (chunks == null || chunks.isEmpty()) return 0f;

        Set<String> queryKeywords = extractKeywords(query);
        if (queryKeywords.isEmpty()) return 0.5f;

        float totalScore = 0f;
        for (SearchResult chunk : chunks) {
            String content = chunk.getContent().toLowerCase();
            long matches = queryKeywords.stream()
                    .filter(content::contains)
                    .count();
            totalScore += (float) matches / queryKeywords.size();
        }

        // Average relevance across all chunks
        float avgRelevance = totalScore / chunks.size();

        // Also factor in vector similarity scores
        float avgVectorScore = (float) chunks.stream()
                .mapToDouble(SearchResult::getScore)
                .average()
                .orElse(0.0);

        return (0.5f * avgRelevance) + (0.5f * avgVectorScore);
    }

    // Measures: is the answer grounded in retrieved chunks?
    // Detects hallucination by checking if answer references
    // content that exists in the retrieved chunks
    private float evaluateAnswerFaithfulness(String answer,
                                             List<SearchResult> chunks) {
        if (answer == null || answer.isBlank()) return 0f;
        if (chunks == null || chunks.isEmpty()) return 0f;

        // Check if answer contains citation markers [1] [2] [3]
        boolean hasCitations = answer.contains("[1]")
                || answer.contains("[2]")
                || answer.contains("[3]");

        // Check if answer mentions file names from chunks
        long fileNameMatches = chunks.stream()
                .filter(c -> answer.contains(
                        c.getFileName().replace(".java", "")
                                .replace(".py", "")))
                .count();

        // Check if answer mentions method names from chunks
        long methodMatches = chunks.stream()
                .filter(c -> c.getMethodName() != null
                        && !c.getMethodName().isEmpty()
                        && answer.contains(c.getMethodName()))
                .count();

        float score = 0f;
        if (hasCitations) score += 0.4f;
        if (fileNameMatches > 0) score += 0.3f;
        if (methodMatches > 0) score += 0.3f;

        // Check for "I could not find" — honest non-answer
        // is better than hallucination
        if (answer.contains("could not find")) {
            return 0.7f; // honest answer scores reasonably
        }

        return Math.min(score, 1.0f);
    }

    // Measures: does the answer actually address the question?
    private float evaluateAnswerRelevance(String query, String answer) {
        if (answer == null || answer.isBlank()) return 0f;

        Set<String> queryKeywords = extractKeywords(query);
        if (queryKeywords.isEmpty()) return 0.5f;

        String answerLower = answer.toLowerCase();
        long matches = queryKeywords.stream()
                .filter(answerLower::contains)
                .count();

        float keywordScore = (float) matches / queryKeywords.size();

        // Penalize very short answers
        float lengthScore = answer.length() < 50 ? 0.3f :
                answer.length() < 200 ? 0.7f : 1.0f;

        return (0.7f * keywordScore) + (0.3f * lengthScore);
    }

    private Set<String> extractKeywords(String text) {
        Set<String> stopWords = Set.of(
                "how", "does", "work", "what", "is", "are",
                "the", "a", "an", "in", "on", "at", "to",
                "for", "of", "and", "or", "with", "this"
        );
        return Arrays.stream(text.toLowerCase().split("\\s+"))
                .filter(w -> w.length() > 2)
                .filter(w -> !stopWords.contains(w))
                .collect(Collectors.toSet());
    }

    private String getQualityLabel(float score) {
        if (score >= 0.8f) return "EXCELLENT";
        if (score >= 0.65f) return "GOOD";
        if (score >= 0.5f) return "MODERATE";
        if (score >= 0.35f) return "POOR";
        return "VERY_POOR";
    }

    private String detectIssues(float contextRelevance,
                                float faithfulness,
                                float relevance) {
        List<String> issues = new java.util.ArrayList<>();
        if (contextRelevance < 0.4f) {
            issues.add("Low context relevance — retrieved chunks " +
                    "may not match the query well");
        }
        if (faithfulness < 0.4f) {
            issues.add("Low faithfulness — answer may contain " +
                    "hallucinated information");
        }
        if (relevance < 0.4f) {
            issues.add("Low answer relevance — answer may not " +
                    "address the question");
        }
        return issues.isEmpty() ? "None" : String.join("; ", issues);
    }
}