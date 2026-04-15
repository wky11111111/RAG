package com.team.rag.bean;

import java.util.List;

public record EvaluationCaseRequest(
        String query,
        String expectedAnswer,
        List<String> expectedDocumentIds,
        String sourceKind,
        String categoryPath,
        String businessScene,
        String fileType,
        String projectName,
        String department,
        String userId,
        String conversationId
) {
}
