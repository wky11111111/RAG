package com.team.rag.mcp;

import java.util.Map;

public record McpToolDefinition(
        String name,
        String description,
        boolean destructive,
        Map<String, Object> inputSchema
) {
}
