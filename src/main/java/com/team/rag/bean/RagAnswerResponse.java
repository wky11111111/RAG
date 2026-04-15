package com.team.rag.bean;

import java.util.List;

public record RagAnswerResponse(
        String answer,
        List<Citation> citations,
        List<RetrievedChunk> retrievedChunks,
        long memoryId,
        long elapsedMs
) {
}
