package com.team.rag.bean;

public record UploadResponse(
        boolean success,
        String message,
        String documentId,
        int chunkCount,
        String indexStatus,
        int documentsInStore
) {
}
