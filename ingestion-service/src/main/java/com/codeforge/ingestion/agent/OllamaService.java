package com.codeforge.ingestion.agent;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class OllamaService {

    private final RestTemplate restTemplate;

    @Value("${ollama.url:http://localhost:11434}")
    private String ollamaUrl;

    private static final String CHAT_MODEL = "qwen2.5:1.5b";

    public String chat(String systemPrompt, String userMessage) {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("model", CHAT_MODEL);
            requestBody.put("prompt", buildPrompt(
                    systemPrompt, userMessage));
            requestBody.put("stream", false);
            requestBody.put("options", Map.of(
                    "temperature", 0.1,  // low temp = more focused
                    "num_predict", 1000  // max tokens in response
            ));

            HttpEntity<Map<String, Object>> request =
                    new HttpEntity<>(requestBody, headers);

            ResponseEntity<Map> response = restTemplate.postForEntity(
                    ollamaUrl + "/api/generate", request, Map.class);

            if (response.getStatusCode() == HttpStatus.OK
                    && response.getBody() != null) {
                return (String) response.getBody().get("response");
            }

        } catch (Exception e) {
            log.error("Error calling Ollama: {}", e.getMessage());
        }
        return null;
    }

    private String buildPrompt(String systemPrompt, String userMessage) {
        return "<|system|>\n" + systemPrompt + "\n<|user|>\n"
                + userMessage + "\n<|assistant|>\n";
    }
}