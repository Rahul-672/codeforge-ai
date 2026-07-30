package com.codeforge.project.controller;

import com.codeforge.common.dto.ApiResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/projects")
public class ProjectController {

    @GetMapping("/health")
    public ApiResponse<?> healthCheck() {
        return ApiResponse.success("Project Manager Service is active", Map.of("status", "UP"));
    }
}
