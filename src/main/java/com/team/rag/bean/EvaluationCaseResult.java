package com.team.rag.bean;

import java.util.List;

public record EvaluationCaseResult(
        String query,
        boolean hitExpectedDocument,
        double recallAtK,
        double precisionAtK,
        List<String> expectedDocumentIds,
        List<String> actualDocumentIds,
        String answer
) {
}
