package com.codeforge.ingestion.agent.orchestrator;

import com.codeforge.ingestion.agent.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class AgentOrchestrator {

    private final BugDiagnosisAgent bugDiagnosisAgent;
    private final CodeReviewAgent codeReviewAgent;
    private final SecurityAgent securityAgent;

    private final ExecutorService executorService =
            Executors.newFixedThreadPool(3);

    public OrchestrationResponse orchestrate(
            OrchestrationRequest request) {

        long startTime = System.currentTimeMillis();

        // Step 1 — determine which agents to run
        List<AgentType> agentsToRun = determineAgents(request);
        log.info("Orchestrator running agents: {} for query: {}",
                agentsToRun, request.getQuery());

        // Step 2 — run agents in parallel
        Map<AgentType, AgentResult> results =
                runAgentsInParallel(request, agentsToRun);

        // Step 3 — combine results
        long totalTime = System.currentTimeMillis() - startTime;
        return buildResponse(results, agentsToRun, totalTime);
    }

    // Classify intent and decide which agents to run
    private List<AgentType> determineAgents(
            OrchestrationRequest request) {

        // If user explicitly specified agents, use those
        if (request.getAgents() != null
                && !request.getAgents().isEmpty()) {
            if (request.getAgents().contains(AgentType.ALL)) {
                return List.of(AgentType.BUG_DIAGNOSIS,
                        AgentType.CODE_REVIEW,
                        AgentType.SECURITY);
            }
            return request.getAgents();
        }

        // Otherwise classify intent from query
        return classifyIntent(request.getQuery());
    }

    private List<AgentType> classifyIntent(String query) {
        String lower = query.toLowerCase();
        List<AgentType> agents = new ArrayList<>();

        // Bug detection keywords
        if (lower.contains("bug") || lower.contains("error")
                || lower.contains("exception")
                || lower.contains("null")
                || lower.contains("crash")
                || lower.contains("fix")
                || lower.contains("stack trace")) {
            agents.add(AgentType.BUG_DIAGNOSIS);
        }

        // Code review keywords
        if (lower.contains("review") || lower.contains("quality")
                || lower.contains("clean") || lower.contains("solid")
                || lower.contains("refactor")
                || lower.contains("improve")
                || lower.contains("best practice")) {
            agents.add(AgentType.CODE_REVIEW);
        }

        // Security keywords
        if (lower.contains("security") || lower.contains("vulnerability")
                || lower.contains("injection")
                || lower.contains("auth")
                || lower.contains("owasp")
                || lower.contains("secure")
                || lower.contains("exploit")) {
            agents.add(AgentType.SECURITY);
        }

        // Default — run all if unclear
        if (agents.isEmpty()) {
            log.info("Intent unclear, running all agents");
            return List.of(AgentType.BUG_DIAGNOSIS,
                    AgentType.CODE_REVIEW,
                    AgentType.SECURITY);
        }

        return agents;
    }

    // Run all selected agents in parallel
    private Map<AgentType, AgentResult> runAgentsInParallel(
            OrchestrationRequest request,
            List<AgentType> agentsToRun) {

        Map<AgentType, Future<AgentResult>> futures = new HashMap<>();

        // Submit all agents to thread pool simultaneously
        for (AgentType agentType : agentsToRun) {
            Future<AgentResult> future = executorService.submit(
                    () -> runAgent(agentType, request));
            futures.put(agentType, future);
        }

        // Collect results
        Map<AgentType, AgentResult> results = new HashMap<>();
        for (Map.Entry<AgentType, Future<AgentResult>> entry
                : futures.entrySet()) {
            try {
                AgentResult result = entry.getValue()
                        .get(60, TimeUnit.SECONDS);
                results.put(entry.getKey(), result);
                log.info("Agent {} completed in {}ms",
                        entry.getKey(),
                        result.getProcessingTimeMs());
            } catch (TimeoutException e) {
                log.error("Agent {} timed out", entry.getKey());
                results.put(entry.getKey(), AgentResult.builder()
                        .agentType(entry.getKey())
                        .success(false)
                        .errorMessage("Agent timed out after 60s")
                        .build());
            } catch (Exception e) {
                log.error("Agent {} failed: {}",
                        entry.getKey(), e.getMessage());
                results.put(entry.getKey(), AgentResult.builder()
                        .agentType(entry.getKey())
                        .success(false)
                        .errorMessage(e.getMessage())
                        .build());
            }
        }

        return results;
    }

    private AgentResult runAgent(AgentType agentType,
                                 OrchestrationRequest request) {
        return switch (agentType) {
            case BUG_DIAGNOSIS -> {
                var bugRequest = new BugDiagnosisRequest();
                bugRequest.setRepositoryId(request.getRepositoryId());
                bugRequest.setBugDescription(request.getQuery());
                bugRequest.setAffectedClass(request.getAffectedClass());
                bugRequest.setErrorType(request.getErrorType());
                var response = bugDiagnosisAgent.diagnose(bugRequest);
                yield AgentResult.builder()
                        .agentType(AgentType.BUG_DIAGNOSIS)
                        .agentName("Bug Diagnosis Agent")
                        .summary(response.getRootCause())
                        .details(response.getExplanation())
                        .recommendations(response.getSuggestedFix()
                                != null ? List.of(
                                response.getSuggestedFix())
                                : List.of())
                        .citations(response.getCitations())
                        .confidence(response.getConfidence())
                        .success(true)
                        .build();
            }
            case CODE_REVIEW ->
                    codeReviewAgent.review(request.getQuery(),
                            request.getRepositoryId());
            case SECURITY ->
                    securityAgent.analyze(request.getQuery(),
                            request.getRepositoryId());
            default -> AgentResult.builder()
                    .agentType(agentType)
                    .success(false)
                    .errorMessage("Unknown agent type")
                    .build();
        };
    }

    private OrchestrationResponse buildResponse(
            Map<AgentType, AgentResult> results,
            List<AgentType> agentsExecuted,
            long totalTime) {

        int successful = (int) results.values().stream()
                .filter(AgentResult::isSuccess).count();
        int failed = results.size() - successful;

        // Combine all recommendations
        List<String> allRecommendations = results.values().stream()
                .filter(AgentResult::isSuccess)
                .filter(r -> r.getRecommendations() != null)
                .flatMap(r -> r.getRecommendations().stream())
                .filter(rec -> rec != null && !rec.isBlank())
                .distinct()
                .collect(Collectors.toList());

        // Find worst severity
        String worstSeverity = findWorstSeverity(results);

        // Build overall summary
        String summary = buildSummary(results, agentsExecuted);

        return OrchestrationResponse.builder()
                .overallSummary(summary)
                .agentResults(results)
                .agentsExecuted(agentsExecuted)
                .combinedRecommendations(allRecommendations)
                .overallSeverity(worstSeverity)
                .totalProcessingTimeMs(totalTime)
                .parallelExecution(agentsExecuted.size() > 1)
                .successfulAgents(successful)
                .failedAgents(failed)
                .build();
    }

    private String findWorstSeverity(
            Map<AgentType, AgentResult> results) {
        List<String> severityOrder = List.of(
                "CRITICAL", "HIGH", "MEDIUM", "LOW", "INFO");

        for (String severity : severityOrder) {
            boolean found = results.values().stream()
                    .filter(AgentResult::isSuccess)
                    .filter(r -> r.getSeverity() != null)
                    .anyMatch(r -> r.getSeverity()
                            .toUpperCase().contains(severity));
            if (found) return severity;
        }
        return "LOW";
    }

    private String buildSummary(
            Map<AgentType, AgentResult> results,
            List<AgentType> agentsExecuted) {

        StringBuilder sb = new StringBuilder();
        sb.append("Analysis complete — ")
                .append(agentsExecuted.size())
                .append(" agent(s) executed. ");

        results.forEach((type, result) -> {
            if (result.isSuccess() && result.getSummary() != null) {
                sb.append(result.getAgentName())
                        .append(": ")
                        .append(result.getSummary())
                        .append(". ");
            }
        });

        return sb.toString().trim();
    }
}