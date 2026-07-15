package com.codeforge.ingestion.rag.chunk;


import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CodeChunk {

    private String id; // unique id for each chunk
    private String content; // actual code which get embedded

    // some data about repo and code
    private String repositoryId;
    private String fileId;
    private String filePath;
    private String fileName;
    private String language;
    private String methodName;
    private String className;
    private int chunkIndex;
    private int startLine;
    private int endLine;
    private String chunkType;

}
