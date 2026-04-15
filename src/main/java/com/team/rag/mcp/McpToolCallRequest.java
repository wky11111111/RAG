package com.team.rag.mcp;

import java.util.Map;

public record McpToolCallRequest(
        String name,
        Map<String, Object> arguments
) {
}
