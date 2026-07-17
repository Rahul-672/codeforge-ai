package com.codeforge.ingestion.agent;

import com.codeforge.ingestion.rag.search.Citation;
import com.codeforge.ingestion.rag.search.EvaluationResult;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BugDiagnosisResponse {
    private String rootCause;

    private String explanation;

    private String suggestedFix;

    private List<String> affectedFiles;

    private List<Citation> citations;

    private EvaluationResult evaluation;

    private String confidence;

    private String rawLlmResponse;
}
