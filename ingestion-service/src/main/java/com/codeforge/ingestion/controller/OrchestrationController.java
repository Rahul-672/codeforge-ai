package com.codeforge.ingestion.controller;

import com.codeforge.common.dto.ApiResponse;
import com.codeforge.ingestion.agent.orchestrator.AgentOrchestrator;
import com.codeforge.ingestion.agent.orchestrator.OrchestrationRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/orchestrator")
@RequiredArgsConstructor
public class OrchestrationController {

    private final AgentOrchestrator agentOrchestrator;

    @PostMapping("/analyze")
    public ApiResponse<?> analyze(
            @Valid @RequestBody OrchestrationRequest request) {
        return ApiResponse.success(
                "Orchestration complete",
                agentOrchestrator.orchestrate(request));
    }
}