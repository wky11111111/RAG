package com.team.rag.bean;

import java.util.List;

public record AgentAnswerResponse(
        String answer,
        List<Citation> citations,
        List<RetrievedChunk> retrievedChunks,
        long memoryId,
        long elapsedMs,
        List<AgentStep> steps
) {
}
