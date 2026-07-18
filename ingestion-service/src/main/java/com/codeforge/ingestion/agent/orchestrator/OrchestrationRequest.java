package com.codeforge.ingestion.agent.orchestrator;

import com.codeforge.ingestion.agent.AgentType;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.util.List;

@Data
public class OrchestrationRequest {

    @NotBlank(message = "Repository ID is required")
    private String repositoryId;

    @NotBlank(message = "Query is required")
    private String query;

    // Which agents to run — if null, orchestrator decides
    private List<AgentType> agents;

    // Optional context
    private String affectedClass;
    private String errorType;
}