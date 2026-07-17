package com.codeforge.ingestion.controller;

import com.codeforge.common.dto.ApiResponse;
import com.codeforge.ingestion.agent.BugDiagnosisAgent;
import com.codeforge.ingestion.agent.BugDiagnosisRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/agent")
@RequiredArgsConstructor
public class AgentController {
    private final BugDiagnosisAgent bugDiagnosisAgent;

    @PostMapping("/diagnose")
    public ApiResponse<?> diagnoseBug(
            @Valid @RequestBody BugDiagnosisRequest request
    ){
        return ApiResponse.success("Bug diagnosis complete", bugDiagnosisAgent.diagnose(request));
    }

}
