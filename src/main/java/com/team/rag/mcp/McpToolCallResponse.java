package com.team.rag.mcp;

public record McpToolCallResponse(
        String name,
        boolean success,
        Object result,
        String error,
        long costTimeMs
) {
    public static McpToolCallResponse success(String name, Object result, long costTimeMs) {
        return new McpToolCallResponse(name, true, result, null, costTimeMs);
    }

    public static McpToolCallResponse failure(String name, String error, long costTimeMs) {
        return new McpToolCallResponse(name, false, null, error, costTimeMs);
    }
}
