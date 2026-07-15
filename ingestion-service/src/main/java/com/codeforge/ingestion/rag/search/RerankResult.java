package com.codeforge.ingestion.rag.search;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RerankResult {
        private SearchResult searchResult;
        private float originalScore;
        private float rerankScore;
        private float finalScore;


}
