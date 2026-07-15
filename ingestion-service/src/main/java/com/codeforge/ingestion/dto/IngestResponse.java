package com.codeforge.ingestion.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class IngestResponse {
    private String repositoryId;
    private String name;
    private String url;
    private String status;
    private String message;
}