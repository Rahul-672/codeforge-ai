package com.codeforge.ingestion.rag.search;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Citation {
    private int index;
    private String fileName;
    private String filePath;
    private String methodName;
    private String language;
    private float relevanceScore;
    private String codeSnippet;
}