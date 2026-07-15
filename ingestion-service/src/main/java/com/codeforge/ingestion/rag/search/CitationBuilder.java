package com.codeforge.ingestion.rag.search;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Component
public class CitationBuilder {

    // Builds a prompt with inline citations
    // Each chunk is tagged [1], [2], [3] etc
    // LLM is instructed to reference these in its answer
    public String buildPromptWithCitations(String query,
                                           List<SearchResult> chunks) {
        StringBuilder prompt = new StringBuilder();

        prompt.append("You are an expert code assistant. ")
                .append("Answer the question using ONLY the code ")
                .append("context provided below.\n")
                .append("When referencing code, use citation numbers ")
                .append("like [1], [2], [3] that match the sources.\n")
                .append("If the answer is not in the provided context, ")
                .append("say 'I could not find this in the codebase'.\n\n");

        prompt.append("=== CODE CONTEXT ===\n\n");

        for (int i = 0; i < chunks.size(); i++) {
            SearchResult chunk = chunks.get(i);
            prompt.append("[").append(i + 1).append("] ")
                    .append("File: ").append(chunk.getFileName());

            if (chunk.getMethodName() != null
                    && !chunk.getMethodName().isEmpty()) {
                prompt.append(" | Method: ")
                        .append(chunk.getMethodName());
            }

            prompt.append(" | Language: ")
                    .append(chunk.getLanguage())
                    .append("\n");
            prompt.append("```").append(chunk.getLanguage()
                    .toLowerCase()).append("\n");
            prompt.append(chunk.getContent()).append("\n");
            prompt.append("```\n\n");
        }

        prompt.append("=== QUESTION ===\n");
        prompt.append(query).append("\n\n");
        prompt.append("=== ANSWER (use [1],[2],[3] to cite sources) ===\n");

        return prompt.toString();
    }

    // Build citation list to return alongside the answer
    public List<Citation> buildCitations(List<SearchResult> chunks) {
        return chunks.stream()
                .map(chunk -> Citation.builder()
                        .index(chunks.indexOf(chunk) + 1)
                        .fileName(chunk.getFileName())
                        .filePath(chunk.getFilePath())
                        .methodName(chunk.getMethodName())
                        .language(chunk.getLanguage())
                        .relevanceScore(chunk.getScore())
                        .codeSnippet(truncate(chunk.getContent(), 200))
                        .build())
                .collect(Collectors.toList());
    }

    private String truncate(String text, int maxLength) {
        if (text == null) return "";
        return text.length() <= maxLength ? text
                : text.substring(0, maxLength) + "...";
    }
}