package com.codeforge.ingestion.rag.guardrail;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

@Slf4j
@Component
public class InputGuardrail {

    private static final List<String> INJECTION_PATTERNS = List.of(
            "ignore previous instructions",
            "ignore all instructions",
            "disregard your instructions",
            "you are now",
            "forget everything",
            "act as",
            "jailbreak"
    );

    private static final int MIN_QUERY_LENGTH = 5;
    private static final int MAX_QUERY_LENGTH = 1000;

    public GuardrailResult validate(String query) {

        if (query == null || query.isBlank()) {
            return new GuardrailResult(false, "Query cannot be empty");
        }

        if (query.length() < MIN_QUERY_LENGTH) {
            return new GuardrailResult(false, "Query too short — please be more specific");
        }

        if (query.length() > MAX_QUERY_LENGTH) {
            return new GuardrailResult(false, "Query too long — maximum 1000 characters");
        }

        String lowerQuery = query.toLowerCase();
        for (String pattern : INJECTION_PATTERNS) {
            if (lowerQuery.contains(pattern)) {
                log.warn("Prompt injection attempt detected: {}", query);
                return new GuardrailResult(false,
                        "Invalid query — please ask a code-related question");
            }
        }

        return new GuardrailResult(true, null);
    }

    // Simple class instead of record to avoid static method conflicts
    public static class GuardrailResult {
        private final boolean allowed;
        private final String reason;

        public GuardrailResult(boolean allowed, String reason) {
            this.allowed = allowed;
            this.reason = reason;
        }

        public boolean isAllowed() {
            return allowed;
        }

        public String getReason() {
            return reason;
        }
    }
}