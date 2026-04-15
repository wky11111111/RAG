package com.team.rag.bean;

import java.util.List;

public record EvaluationRunResponse(
        int totalCases,
        double averageRecallAtK,
        double averagePrecisionAtK,
        double hitRate,
        List<EvaluationCaseResult> results
) {
}
