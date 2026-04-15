package com.team.rag.bean;

public record RetrievedChunk(
        String chunkId,
        String documentId,
        String documentName,
        String docType,
        double score,
        String content
) {
}
