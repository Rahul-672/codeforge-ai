package com.codeforge.ingestion.controller;

import com.codeforge.common.dto.ApiResponse;
import com.codeforge.ingestion.dto.IngestRequest;
import com.codeforge.ingestion.service.IngestionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/ingest")
@RequiredArgsConstructor
public class IngestionController {

    private final IngestionService ingestionService;

    @PostMapping("/repository")
    public ApiResponse<?> ingestRepository(
            @Valid @RequestBody IngestRequest request,
            @RequestHeader(value = "X-User-Email", defaultValue = "anonymous") String userEmail) {

        return ApiResponse.success(
                "Ingestion started",
                ingestionService.startIngestion(request, userEmail));
    }

    @GetMapping("/repository/{id}/status")
    public ApiResponse<?> getStatus(@PathVariable String id) {
        return ApiResponse.success(ingestionService.getStatus(id));
    }

    @GetMapping("/repositories")
    public ApiResponse<?> getUserRepositories(
            @RequestHeader(value = "X-User-Email", defaultValue = "anonymous") String userEmail) {
        return ApiResponse.success(ingestionService.getUserRepositories(userEmail));
    }
}