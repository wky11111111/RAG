package com.team.rag.bean;

public record AgentStep(
        String name,
        String status,
        String detail,
        long costTimeMs
) {
}
