package com.team.rag.bean;

public record ChunkView(
        String chunkId,
        int chunkIndex,
        int tokenCount,
        String content
) {
}
