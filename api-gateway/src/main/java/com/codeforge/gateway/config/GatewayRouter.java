package com.codeforge.gateway.config;

import com.codeforge.gateway.filter.JwtUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.server.*;
import reactor.core.publisher.Mono;

@Slf4j
@Configuration
@RequiredArgsConstructor
public class GatewayRouter {

    private final JwtUtil jwtUtil;

    @Value("${services.auth-url}")
    private String authUrl;

    @Value("${services.project-url}")
    private String projectUrl;

    @Value("${services.ingestion-url}")
    private String ingestionUrl;

    private final WebClient.Builder webClientBuilder;

    @Bean
    public RouterFunction<ServerResponse> routes() {
        return RouterFunctions
                .route(RequestPredicates.path("/api/auth/**"), this::proxyToAuth)
                .andRoute(RequestPredicates.path("/api/projects/**"), this::proxyToProject)
                .andRoute(RequestPredicates.path("/api/ingest/**"), this::proxyToIngestion);
    }

    private Mono<ServerResponse> proxyToAuth(ServerRequest request) {
        return proxyRequest(request, authUrl, false);
    }

    private Mono<ServerResponse> proxyToProject(ServerRequest request) {
        return proxyRequest(request, projectUrl, true);
    }

    private Mono<ServerResponse> proxyToIngestion(ServerRequest request) {
        return proxyRequest(request, ingestionUrl, true);
    }

    private Mono<ServerResponse> proxyRequest(
            ServerRequest request,
            String targetUrl,
            boolean requireAuth) {

        if (requireAuth) {
            String authHeader = request.headers()
                    .firstHeader(HttpHeaders.AUTHORIZATION);

            if (authHeader == null || !authHeader.startsWith("Bearer ")) {
                return ServerResponse.status(HttpStatus.UNAUTHORIZED)
                        .bodyValue("{\"success\":false,\"message\":\"Missing token\"}");
            }

            String token = authHeader.substring(7);
            if (!jwtUtil.isTokenValid(token)) {
                return ServerResponse.status(HttpStatus.UNAUTHORIZED)
                        .bodyValue("{\"success\":false,\"message\":\"Invalid token\"}");
            }

            String email = jwtUtil.extractEmail(token);
            String role = jwtUtil.extractRole(token);
            log.info("Authenticated: {} [{}]", email, role);
        }

        String targetPath = targetUrl + request.path();

        return webClientBuilder.build()
                .method(request.method())
                .uri(targetPath)
                .headers(headers -> headers.addAll(request.headers().asHttpHeaders()))
                .body(request.bodyToMono(String.class), String.class)
                .retrieve()
                .toEntity(String.class)
                .flatMap(response -> ServerResponse
                        .status(response.getStatusCode())
                        .headers(h -> h.addAll(response.getHeaders()))
                        .bodyValue(response.getBody() != null ? response.getBody() : ""));
    }
}