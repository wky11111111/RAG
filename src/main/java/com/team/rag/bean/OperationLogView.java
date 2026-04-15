package com.team.rag.bean;

import java.time.Instant;

public record OperationLogView(
        Long id,
        String traceId,
        String userId,
        String sessionId,
        String module,
        String action,
        String status,
        Long costTime,
        Instant timestamp,
        String detailJson,
        String errorStack
) {
}
