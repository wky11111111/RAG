package com.team.rag.bean;

import java.time.Instant;

public record DocumentSummary(
        String id,
        String name,
        String docType,
        String description,
        int chunkCount,
        String indexStatus,
        String lastError,
        Instant uploadedAt,
        String sourceKind,
        String categoryPath,
        String businessScene,
        String fileType,
        String projectName,
        String department,
        String userId,
        String conversationId,
        Long fileSizeBytes
) {
}
