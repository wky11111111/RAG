package com.team.rag.bean;

import java.util.List;
import java.util.Map;

public record UpsertVector(
        String id,
        List<Float> values,
        Map<String, Object> metadata
) {
}
