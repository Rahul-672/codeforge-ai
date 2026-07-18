package com.codeforge.ingestion.agent.orchestrator;

import com.codeforge.ingestion.agent.AgentResult;
import com.codeforge.ingestion.agent.AgentType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrchestrationResponse {

    // Overall summary combining all agents
    private String overallSummary;

    // Results from each agent
    private Map<AgentType, AgentResult> agentResults;

    // Which agents were run
    private List<AgentType> agentsExecuted;

    // Combined recommendations from all agents
    private List<String> combinedRecommendations;

    // Worst severity across all agents
    private String overallSeverity;

    // Total time taken
    private long totalProcessingTimeMs;

    // Were agents run in parallel?
    private boolean parallelExecution;

    // How many agents succeeded
    private int successfulAgents;
    private int failedAgents;
}