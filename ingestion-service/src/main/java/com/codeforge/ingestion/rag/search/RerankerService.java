package com.codeforge.ingestion.rag.search;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
public class RerankerService {

    // Rerank retrieved chunks based on multiple signals
    // Returns top-K most relevant chunks after reranking
    public List<SearchResult> rerank(String query,
                                     List<SearchResult> candidates,
                                     int topK) {
        if (candidates == null || candidates.isEmpty()) {
            return List.of();
        }

        log.info("Reranking {} candidates for query: '{}'",
                candidates.size(), query);

        // Extract query keywords for matching
        Set<String> queryKeywords = extractKeywords(query);

        // Score each candidate
        List<RerankResult> scored = candidates.stream()
                .map(candidate -> scoreCandidate(
                        candidate, queryKeywords, query))
                .sorted(Comparator.comparingDouble(
                        RerankResult::getFinalScore).reversed())
                .collect(Collectors.toList());

        // Log reranking results
        scored.forEach(r -> log.debug(
                "Reranked: {} | original: {:.3f} | rerank: {:.3f} | final: {:.3f}",
                r.getSearchResult().getFileName(),
                r.getOriginalScore(),
                r.getRerankScore(),
                r.getFinalScore()));

        // Return top-K after reranking
        return scored.stream()
                .limit(topK)
                .map(RerankResult::getSearchResult)
                .collect(Collectors.toList());
    }

    private RerankResult scoreCandidate(SearchResult candidate,
                                        Set<String> queryKeywords,
                                        String query) {
        float originalScore = candidate.getScore();
        float rerankScore = computeRerankScore(
                candidate, queryKeywords, query);

        // Final score = 60% original vector score + 40% rerank score
        // Vector score captures semantic similarity
        // Rerank score captures keyword and structural relevance
        float finalScore = (0.6f * originalScore) + (0.4f * rerankScore);

        return RerankResult.builder()
                .searchResult(candidate)
                .originalScore(originalScore)
                .rerankScore(rerankScore)
                .finalScore(finalScore)
                .build();
    }

    private float computeRerankScore(SearchResult candidate,
                                     Set<String> queryKeywords,
                                     String query) {
        float score = 0.0f;
        String content = candidate.getContent().toLowerCase();
        String queryLower = query.toLowerCase();

        // Signal 1 — Keyword overlap (40% weight)
        // How many query keywords appear in the chunk?
        long matchedKeywords = queryKeywords.stream()
                .filter(content::contains)
                .count();
        float keywordScore = queryKeywords.isEmpty() ? 0f :
                (float) matchedKeywords / queryKeywords.size();
        score += 0.4f * keywordScore;

        // Signal 2 — Method name relevance (20% weight)
        // If query keyword matches method name, boost significantly
        if (candidate.getMethodName() != null
                && !candidate.getMethodName().isEmpty()) {
            String methodLower = candidate.getMethodName().toLowerCase();
            boolean methodMatches = queryKeywords.stream()
                    .anyMatch(k -> methodLower.contains(k)
                            || k.contains(methodLower));
            if (methodMatches) score += 0.2f;
        }

        // Signal 3 — Content length normalization (15% weight)
        // Prefer chunks that are substantial but not too long
        int contentLength = candidate.getContent().length();
        float lengthScore;
        if (contentLength < 50) {
            lengthScore = 0.2f;  // too short, probably not useful
        } else if (contentLength < 200) {
            lengthScore = 0.7f;  // good short chunk
        } else if (contentLength < 800) {
            lengthScore = 1.0f;  // ideal length
        } else {
            lengthScore = 0.6f;  // too long, less focused
        }
        score += 0.15f * lengthScore;

        // Signal 4 — Language relevance (15% weight)
        // Prefer code files over config/html for code questions
        boolean isCodeQuery = isCodeRelatedQuery(queryLower);
        if (isCodeQuery) {
            float langScore = switch (candidate.getLanguage()) {
                case "Java", "Python", "JavaScript",
                     "TypeScript", "Go" -> 1.0f;
                case "SQL" -> 0.8f;
                case "XML", "YAML", "JSON" -> 0.5f;
                case "HTML" -> 0.3f;
                default -> 0.4f;
            };
            score += 0.15f * langScore;
        } else {
            score += 0.15f * 0.5f;
        }

        // Signal 5 — Exact phrase match bonus (10% weight)
        // If the exact query phrase appears in content, big boost
        if (content.contains(queryLower)) {
            score += 0.1f;
        }

        return Math.min(score, 1.0f);
    }

    private Set<String> extractKeywords(String query) {
        // Stop words to ignore
        Set<String> stopWords = Set.of(
                "how", "does", "work", "what", "is", "are",
                "the", "a", "an", "in", "on", "at", "to",
                "for", "of", "and", "or", "with", "this",
                "that", "it", "be", "was", "has", "have",
                "do", "did", "can", "could", "would", "should"
        );

        return Arrays.stream(query.toLowerCase().split("\\s+"))
                .filter(word -> word.length() > 2)
                .filter(word -> !stopWords.contains(word))
                .collect(Collectors.toSet());
    }

    private boolean isCodeRelatedQuery(String query) {
        List<String> codeTerms = List.of(
                "method", "function", "class", "interface",
                "implement", "code", "logic", "how does",
                "where is", "find", "search", "query",
                "endpoint", "api", "service", "repository"
        );
        return codeTerms.stream()
                .anyMatch(query::contains);
    }
}