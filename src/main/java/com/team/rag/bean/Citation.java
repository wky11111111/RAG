package com.team.rag.bean;

public record Citation(
        String documentId,
        String documentName,
        double score,
        String docType,
        int hitCount
) {
}
