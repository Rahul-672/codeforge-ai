package com.codeforge.ingestion.agent;

import com.codeforge.ingestion.rag.search.Citation;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AgentResult {
    private AgentType agentType;
    private String agentName;
    private String summary;
    private String details;
    private List<String> recommendations;
    private List<Citation> citations;
    private String severity;
    private String confidence;
    private float score;
    private long processingTimeMs;
    private boolean success;
    private String errorMessage;
}
