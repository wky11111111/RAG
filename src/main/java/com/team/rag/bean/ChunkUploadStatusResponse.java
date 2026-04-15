package com.team.rag.bean;

import java.util.List;

public record ChunkUploadStatusResponse(
        String uploadId,
        String fileName,
        long fileSize,
        long chunkSize,
        int totalChunks,
        List<Integer> uploadedChunks,
        boolean completed
) {
}
