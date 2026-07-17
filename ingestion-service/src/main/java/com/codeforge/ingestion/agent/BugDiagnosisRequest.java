package com.codeforge.ingestion.agent;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class BugDiagnosisRequest {
    @NotBlank(message = "Repository ID is required")
    private String repositoryId;

    @NotBlank(message = "Bug description or Stack is required")
    private String bugDescription;

    private String affectedClass;

    private String errorType;
}
