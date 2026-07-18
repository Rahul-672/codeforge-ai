package com.codeforge.ingestion.agent;

import com.codeforge.ingestion.rag.search.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class CodeReviewAgent {
    private final SearchService searchService;
    private final OllamaService ollamaService;

    private static final String SYSTEM_PROMPT = """
            You are an expert code reviewer specializing in Java
            and Spring Boot applications.
            
            Analyze the provided code for:
            1. SOLID principle violations
            2. Code smells (long methods, duplicate code, etc)
            3. Error handling issues
            4. Performance concerns
            5. Maintainability issues
            
            Format your response EXACTLY like this:
            SUMMARY: [one sentence overall assessment]
            ISSUES: [comma separated list of issues found]
            RECOMMENDATIONS: [pipe | separated list of specific recommendations]
            SEVERITY: [CRITICAL/HIGH/MEDIUM/LOW based on worst issue]
            SCORE: [0-10 where 10 is perfect code]
            CONFIDENCE: [HIGH/MEDIUM/LOW]
            """;

    public AgentResult review(String query, String repositoryId){
        long startTime = System.currentTimeMillis();
        log.info("Code Review Agent starting for query: {} ", query);

        try {
            RAGResponse ragResponse = searchService.search(query, repositoryId);

            List<SearchResult> chunks = ragResponse.getRetrievedChunks();

            if (chunks == null || chunks.isEmpty()) {
                return AgentResult.builder()
                        .agentType(AgentType.CODE_REVIEW)
                        .agentName("CODE review agent").success(false)
                        .processingTimeMs(System.currentTimeMillis() - startTime)
                        .errorMessage("No relevant code found.")
                        .build();
            }

            String codeContext = buildCodeContext(chunks);
            String userMessage = "Please review this code : \n\n" + codeContext + "\nFocus on : " + query;

            String llmResponse = ollamaService.chat(SYSTEM_PROMPT, userMessage);

            if (llmResponse == null) {
                return AgentResult.builder()
                        .agentType(AgentType.CODE_REVIEW)
                        .agentName("code review agent ")
                        .success(false)
                        .errorMessage("LLM unavailable")
                        .processingTimeMs(System.currentTimeMillis() - startTime)
                        .build();
            }

            return AgentResult.builder()
                    .agentType(AgentType.CODE_REVIEW)
                    .agentName("Code Review Agent")
                    .summary(extractField(llmResponse, "SUMMARY:"))
                    .details(llmResponse)
                    .recommendations(parseRecommendations(
                            extractField(llmResponse,
                                    "RECOMMENDATIONS:")))
                    .severity(extractField(llmResponse, "SEVERITY:"))
                    .score(parseScore(
                            extractField(llmResponse, "SCORE:")))
                    .confidence(extractField(
                            llmResponse, "CONFIDENCE:"))
                    .citations(ragResponse.getCitations())
                    .success(true)
                    .processingTimeMs(
                            System.currentTimeMillis() - startTime)
                    .build();


        }
        catch (Exception e){
            log.error("CODE review agent failed: {}", e.getMessage());

            return AgentResult.builder()
                    .agentType(AgentType.CODE_REVIEW)
                    .agentName("Code Review Agent")
                    .success(false)
                    .errorMessage(e.getMessage())
                    .processingTimeMs(
                            System.currentTimeMillis() - startTime)
                    .build();
        }
    }

    private String buildCodeContext(List<SearchResult> chunks){
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < chunks.size(); i++) {
            SearchResult chunk = chunks.get(i);
            sb.append("[").append(i + 1).append("] ")
                    .append(chunk.getFileName()).append("\n```")
                    .append(chunk.getLanguage().toLowerCase())
                    .append("\n").append(chunk.getContent())
                    .append("\n```\n\n");
        }
        return sb.toString();
    }

    private List<String> parseRecommendations(String raw){
        List<String> recommendations = new ArrayList<>();
        if (raw == null || raw.isBlank()) return recommendations;
        for (String rec : raw.split("\\|")) {
            String trimmed = rec.trim();
            if (!trimmed.isEmpty()) {
                recommendations.add(trimmed);
            }
        }
        return recommendations;
    }

    private float parseScore(String scoreStr){
        try{
            if(scoreStr == null) return 5.0f;
            return Float.parseFloat(scoreStr.trim().replaceAll("[^0-9.]", ""));
        }
        catch(Exception e){
            return 5.0f;
        }
    }

    private String extractField(String response, String fieldName){
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
