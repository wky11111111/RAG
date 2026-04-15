package com.team.rag.bean;

import java.util.Map;

public record PineconeMatch(
        String id,
        double score,
        Map<String, Object> metadata
) {
}
