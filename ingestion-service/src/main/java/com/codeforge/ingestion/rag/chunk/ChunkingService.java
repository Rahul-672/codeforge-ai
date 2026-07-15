package com.codeforge.ingestion.rag.chunk;


import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

@Service
@Slf4j
public class ChunkingService {

    private static final int MAX_CHUNK_SIZE = 1500;
    private static final int MIN_CHUNK_SIZE = 50;

    public List<CodeChunk> chunkFile(
            String content,
            String repositoryId,
            String fileId,
            String filePath,
            String fileName,
            String language
    ){

        if(content == null || content.isBlank()){
            return List.of();
        }

        List<CodeChunk> chunks = new ArrayList<>();

        if(isCodeFile(language)){
            chunks = chunkByFunctions(content, repositoryId, fileId, filePath, fileName, language);
        }
        else{
            chunks = chunkByParagraph(content, repositoryId, fileId, filePath, fileName, language);
        }


        log.debug("Chunked {} into {} chunks (language: {})", fileName, chunks.size(), language );
        return chunks;
    }


    private List<CodeChunk> chunkByFunctions(String content,
                                             String repositoryId,
                                             String fileId,
                                             String filePath,
                                             String fileName,
                                             String language){
        List<CodeChunk> chunks = new ArrayList<>();
        String[] lines = content.split("\n");

        StringBuilder currentChunk = new StringBuilder();
        int chunkStartLine = 1;
        int chunkIndex = 0;
        String currentMethod = null;


        for(int i=0; i< lines.length; i++){
            String line = lines[i];

            boolean isMethodStart = isMethodBoundary(line , language);

            if(isMethodStart && currentChunk.length() > MIN_CHUNK_SIZE){
                chunks.add(buildChunk(
                        currentChunk.toString(),
                        repositoryId, fileId, filePath, fileName,
                        language, currentMethod, chunkIndex,
                        chunkStartLine, i
                ));

                currentChunk = new StringBuilder();
                chunkStartLine = i+1;
                chunkIndex++;
                currentMethod = extractMethodName(line);
            }

            currentChunk.append(line).append("\n");

            if(currentChunk.length() > MAX_CHUNK_SIZE){
                chunks.add(buildChunk(
                        currentChunk.toString(),
                        repositoryId, fileId, filePath, fileName,
                        language, currentMethod, chunkIndex,
                        chunkStartLine, i
                ));

                currentChunk = new StringBuilder();
                chunkStartLine = i+1;
                chunkIndex++;
            }
        }

        if(currentChunk.length() > MIN_CHUNK_SIZE){
            chunks.add(buildChunk(
                    currentChunk.toString(),
                    repositoryId, fileId, filePath, fileName,
                    language, currentMethod, chunkIndex,
                    chunkStartLine, lines.length
            ));
        }

        return chunks;
    }

    private List<CodeChunk> chunkByParagraph(String content, String repositoryId, String fileId, String filePath, String fileName, String language){
        List<CodeChunk> chunks = new ArrayList<>();
        String[] paragraphs = content.split("\n\n");
        int chunkIndex = 0;

        for(String paragraph : paragraphs){
            if(paragraph.trim().length() < MIN_CHUNK_SIZE)
                continue;

            if(paragraph.trim().length() > MAX_CHUNK_SIZE){
                List<String> subChunks = splitLargeParagraph(paragraph);
                for(String sub : subChunks){
                    chunks.add(buildChunk(
                            sub, repositoryId, fileId,
                            filePath, fileName, language,
                            null, chunkIndex, 0, 0
                    ));

                    chunkIndex++;
                }
            }
            else{
                chunks.add(buildChunk(
                        paragraph, repositoryId, fileId,
                        filePath, fileName, language,
                        null, chunkIndex, 0, 0
                ));

                chunkIndex++;
            }
        }

        return chunks;
    }

    private List<String> splitLargeParagraph(String paragraph){
        List<String> parts = new ArrayList<>();

        int start = 0;

        while(start < paragraph.length()){
            int end = Math.min(start + MAX_CHUNK_SIZE, paragraph.length());
            parts.add(paragraph.substring(start, end));
            start = end;
        }

        return parts;
    }


    private boolean isMethodBoundary(String line, String language){
        String trimmed = line.trim();
        switch (language) {
            case "Java", "Kotlin" -> {
                return (trimmed.contains("public ") ||
                        trimmed.contains("private ") ||
                        trimmed.contains("protected ")) &&
                        trimmed.contains("(") &&
                        !trimmed.startsWith("//") &&
                        !trimmed.startsWith("*");
            }
            case "Python" -> {
                return trimmed.startsWith("def ") ||
                        trimmed.startsWith("async def ");
            }
            case "JavaScript", "TypeScript" -> {
                return trimmed.startsWith("function ") ||
                        trimmed.contains("=> {") ||
                        trimmed.startsWith("async function");
            }
            case "Go" -> {
                return trimmed.startsWith("func ");
            }
            default -> {
                return false;
            }
        }
    }


    private String extractMethodName(String line){
        try{
            String trimmed = line.trim();
            int parenIdx = trimmed.indexOf("(");
            if(parenIdx == -1){
                String beforeParen = trimmed.substring(0, parenIdx);
                String[] parts = beforeParen.trim().split("\\s+");
                return parts[parts.length - 1];
            }
        }
        catch(Exception e){}

        return null;
    }


    private CodeChunk buildChunk(String content,
                                 String repositoryId,
                                 String fileId,
                                 String filePath,
                                 String fileName,
                                 String language,
                                 String methodName,
                                 int chunkIndex,
                                 int startLine,
                                 int endLine){
        return CodeChunk.builder()
                .id(UUID.randomUUID().toString())
                .content(content.trim())
                .repositoryId(repositoryId)
                .fileId(fileId)
                .filePath(filePath)
                .fileName(fileName)
                .language(language)
                .methodName(methodName)
                .chunkIndex(chunkIndex)
                .startLine(startLine)
                .endLine(endLine)
                .chunkType(methodName != null ? "METHOD" : "BLOCK")
                .build();

    }

    private boolean isCodeFile(String language){
        return switch (language){
            case "Java", "Python", "JavaScript",
                 "TypeScript", "Go", "C++",
                 "C", "Kotlin", "Rust" -> true;
            default -> false;
        };
    }
}
