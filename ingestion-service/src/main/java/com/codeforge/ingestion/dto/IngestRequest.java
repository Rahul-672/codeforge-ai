package com.codeforge.ingestion.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class IngestRequest {

    @NotBlank(message = "Repository URL is required")
    private String url;

    private String branch;  // null = auto-detect default branch

    private String description;
}