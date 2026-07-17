package com.codeforge.ingestion.agent;

import com.codeforge.ingestion.rag.guardrail.InputGuardrail;
import com.codeforge.ingestion.rag.search.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import java.util.List;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class BugDiagnosisAgent {

    private final SearchService searchService;
    private final OllamaService ollamaService;
    private final InputGuardrail inputGuardrail;

    private static final String SYSTEM_PROMPT = """
            You are an expert Java/Spring Boot debugging assistant.
            You will be given:
            1. A bug description or stack trace
            2. Relevant source code from the repository
            
            Your task is to:
            - Identify the ROOT CAUSE of the bug
            - Explain WHY it happens in simple terms
            - Suggest a SPECIFIC FIX with code if possible
            - List the AFFECTED FILES
            
            Format your response EXACTLY like this:
            ROOT_CAUSE: [one sentence describing the root cause]
            EXPLANATION: [2-3 sentences explaining why this happens]
            SUGGESTED_FIX: [specific fix with code snippet if possible]
            AFFECTED_FILES: [comma separated list of file names]
            CONFIDENCE: [HIGH/MEDIUM/LOW based on how certain you are]
            """;

    public BugDiagnosisResponse diagnose(BugDiagnosisRequest request) {
        log.info("Bug Diagnosis Agent starting for repo: {}",
                request.getRepositoryId());

        // Step 1 — validate input
        var guardrailResult = inputGuardrail.validate(
                request.getBugDescription());
        if (!guardrailResult.isAllowed()) {
            return BugDiagnosisResponse.builder()
                    .rootCause("Invalid input: " +
                            guardrailResult.getReason())
                    .confidence("LOW")
                    .build();
        }

        // Step 2 — extract search query from bug description
        String searchQuery = extractSearchQuery(
                request.getBugDescription(),
                request.getAffectedClass());

        log.info("Searching codebase with query: {}", searchQuery);

        // Step 3 — RAG search with reranking
        RAGResponse ragResponse = searchService.search(
                searchQuery, request.getRepositoryId());

        List<SearchResult> relevantChunks =
                ragResponse.getRetrievedChunks();

        if (relevantChunks == null || relevantChunks.isEmpty()) {
            return BugDiagnosisResponse.builder()
                    .rootCause("Could not find relevant code in " +
                            "the repository for this bug")
                    .confidence("LOW")
                    .build();
        }

        // Step 4 — build context for LLM
        String codeContext = buildCodeContext(relevantChunks);
        String userMessage = buildUserMessage(
                request.getBugDescription(), codeContext);

        log.info("Calling LLM with {} code chunks as context",
                relevantChunks.size());

        // Step 5 — call LLM
        String llmResponse = ollamaService.chat(
                SYSTEM_PROMPT, userMessage);

        if (llmResponse == null) {
            return BugDiagnosisResponse.builder()
                    .rootCause("LLM service unavailable")
                    .confidence("LOW")
                    .build();
        }

        log.info("LLM response received, parsing...");

        // Step 6 — parse LLM response
        BugDiagnosisResponse response = parseLlmResponse(llmResponse);

        // Step 7 — add citations and evaluation
        response.setCitations(ragResponse.getCitations());
        response.setEvaluation(ragResponse.getEvaluation());
        response.setRawLlmResponse(llmResponse);

        log.info("Bug diagnosis complete. Confidence: {}",
                response.getConfidence());

        return response;
    }

    private String extractSearchQuery(String bugDescription,
                                       String affectedClass) {
        // Extract the most relevant part of the stack trace
        // Focus on the first few lines which have the error type
        String[] lines = bugDescription.split("\n");

        StringBuilder query = new StringBuilder();

        // Add affected class if provided
        if (affectedClass != null && !affectedClass.isBlank()) {
            query.append(affectedClass).append(" ");
        }

        // Add first meaningful line of stack trace
        for (String line : lines) {
            line = line.trim();
            if (!line.isEmpty() && !line.startsWith("at ")
                    && !line.startsWith("...")) {
                query.append(line, 0,
                        Math.min(line.length(), 100));
                break;
            }
        }

        // If query is too short, use full description
        if (query.length() < 10) {
            query.append(bugDescription, 0,
                    Math.min(bugDescription.length(), 200));
        }

        return query.toString().trim();
    }

    private String buildCodeContext(List<SearchResult> chunks) {
        StringBuilder context = new StringBuilder();
        context.append("=== RELEVANT CODE FROM REPOSITORY ===\n\n");

        for (int i = 0; i < chunks.size(); i++) {
            SearchResult chunk = chunks.get(i);
            context.append("[").append(i + 1).append("] ")
                   .append(chunk.getFileName());

            if (chunk.getMethodName() != null
                    && !chunk.getMethodName().isEmpty()) {
                context.append(" → ")
                       .append(chunk.getMethodName());
            }

            context.append("\n```")
                   .append(chunk.getLanguage().toLowerCase())
                   .append("\n")
                   .append(chunk.getContent())
                   .append("\n```\n\n");
        }

        return context.toString();
    }

    private String buildUserMessage(String bugDescription,
                                     String codeContext) {
        return "BUG REPORT:\n" + bugDescription
                + "\n\n" + codeContext
                + "\nPlease diagnose this bug using the code above.";
    }

    private BugDiagnosisResponse parseLlmResponse(String response) {
        BugDiagnosisResponse result = new BugDiagnosisResponse();

        result.setRootCause(extractField(response, "ROOT_CAUSE:"));
        result.setExplanation(extractField(response, "EXPLANATION:"));
        result.setSuggestedFix(extractField(response,
                "SUGGESTED_FIX:"));
        result.setConfidence(extractField(response, "CONFIDENCE:"));

        // Parse affected files
        String filesStr = extractField(response, "AFFECTED_FILES:");
        if (filesStr != null && !filesStr.isBlank()) {
            ArrayList<String> affectedFiles = new ArrayList<>();
            for (String file : filesStr.split(",")) {
                String trimmed = file.trim();
                if (!trimmed.isEmpty()) {
                    affectedFiles.add(trimmed);
                }
            }
            result.setAffectedFiles(affectedFiles);
        }

        // Default confidence if not parsed
        if (result.getConfidence() == null
                || result.getConfidence().isBlank()) {
            result.setConfidence("MEDIUM");
        }

        return result;
    }

    private String extractField(String response, String fieldName) {
        try {
            int startIdx = response.indexOf(fieldName);
            if (startIdx == -1) return null;

            startIdx += fieldName.length();
            int endIdx = response.indexOf("\n", startIdx);

            String value = endIdx == -1
                    ? response.substring(startIdx)
                    : response.substring(startIdx, endIdx);

            return value.trim();
        } catch (Exception e) {
            return null;
        }
    }
}