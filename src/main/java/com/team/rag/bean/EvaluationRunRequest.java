package com.team.rag.bean;

import java.util.List;

public record EvaluationRunRequest(
        List<EvaluationCaseRequest> cases,
        Integer topK
) {
}
