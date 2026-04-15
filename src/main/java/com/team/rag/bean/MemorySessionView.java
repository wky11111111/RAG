package com.team.rag.bean;

public record MemorySessionView(
        long memoryId,
        String title,
        int roundCount,
        String lastPreview
) {
}
