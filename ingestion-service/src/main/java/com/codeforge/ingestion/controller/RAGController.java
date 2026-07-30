package com.codeforge.ingestion.controller;

import com.codeforge.common.dto.ApiResponse;
import com.codeforge.ingestion.rag.guardrail.InputGuardrail;
import com.codeforge.ingestion.rag.search.RAGResponse;
import com.codeforge.ingestion.rag.search.SearchService;
import com.codeforge.ingestion.rag.embedding.EmbeddingService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@CrossOrigin(origins = "*", allowedHeaders = "*")
@RestController
@RequestMapping("/api/rag")
@RequiredArgsConstructor
public class RAGController {

    private final SearchService searchService;
    private final EmbeddingService embeddingService;
    private final InputGuardrail inputGuardrail;

    @PostMapping("/embed/{repositoryId}")
    public ApiResponse<?> embedRepository(
            @PathVariable String repositoryId) {
        embeddingService.embedRepository(repositoryId);
        return ApiResponse.success(
                "Embedding started for repository: "
                        + repositoryId, null);
    }

    @PostMapping("/search")
    public ApiResponse<?> search(
            @RequestBody Map<String, String> request,
            // ── TEST — BENCHMARK ONLY ──────────────────────────────
            // Pass ?bypassCache=true to skip Redis and measure raw
            // pipeline latency (BEFORE optimization baseline).
            // Default: false (uses Redis cache = AFTER optimization).
            // ────────────────────────────────────────────────────────
            @RequestParam(value = "bypassCache",
                    defaultValue = "false") boolean bypassCache) {

        String query = request.get("query");
        String repositoryId = request.get("repositoryId");

        // Apply input guardrail
        var guardrailResult = inputGuardrail.validate(query);
        if (!guardrailResult.isAllowed()) {
            return ApiResponse.error(guardrailResult.getReason());
        }

        // ── TEST — BENCHMARK ONLY ──────────────────────────────
        // bypassCache=true → calls searchNoCache() (skips Redis)
        // bypassCache=false → calls search() (uses @Cacheable Redis)
        // ────────────────────────────────────────────────────────
        RAGResponse response;
        if (bypassCache) {
            response = searchService.searchNoCache(query, repositoryId);
        } else {
            response = searchService.search(query, repositoryId);
        }

        return ApiResponse.success("Search completed", response);
    }
}