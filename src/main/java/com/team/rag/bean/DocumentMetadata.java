package com.team.rag.bean;

public record DocumentMetadata(
        String sourceKind,
        String categoryPath,
        String businessScene,
        String fileType,
        String projectName,
        String department,
        String userId,
        String conversationId,
        String checksum,
        Long fileSizeBytes
) {
}
