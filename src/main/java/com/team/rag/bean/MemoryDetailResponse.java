package com.team.rag.bean;

import java.util.List;

public record MemoryDetailResponse(
        long memoryId,
        int roundCount,
        List<MemoryRoundView> rounds,
        List<ChatMessage> messages,
        String summary,
        int tokenCount,
        int summaryThreshold,
        boolean summarized
) {
}
