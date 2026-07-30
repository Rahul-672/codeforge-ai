package com.codeforge.ingestion.rag.search;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.io.Serializable;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SearchResult implements Serializable {
    private String content;
    private String filePath;
    private String fileName;
    private String language;
    private String methodName;
    private float score;
    private int chunkIndex;
}