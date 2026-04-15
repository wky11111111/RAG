package com.team.rag.mcp;

import com.team.rag.agent.skill.AgentSkill;
import com.team.rag.service.ObservabilityService;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class RagMcpToolService {

    private final Map<String, AgentSkill> handlers = new LinkedHashMap<>();
    private final List<McpToolDefinition> definitions;
    private final ObservabilityService observabilityService;

    public RagMcpToolService(List<AgentSkill> skills, ObservabilityService observabilityService) {
        this.observabilityService = observabilityService;
        for (AgentSkill skill : skills) {
            this.handlers.put(skill.name(), skill);
        }
        this.definitions = skills.stream()
                .map(skill -> new McpToolDefinition(
                        skill.name(),
                        skill.description(),
                        skill.destructive(),
                        skill.inputSchema()
                ))
                .toList();
    }

    public List<McpToolDefinition> listTools() {
        return definitions;
    }

    public McpToolCallResponse call(McpToolCallRequest request) {
        long start = System.currentTimeMillis();
        String toolName = request == null ? "" : request.name();
        Map<String, Object> args = request == null || request.arguments() == null
                ? Map.of()
                : request.arguments();
        try {
            AgentSkill handler = handlers.get(toolName);
            if (handler == null) {
                throw new IllegalArgumentException("Unknown MCP tool: " + toolName);
            }
            Object result = observabilityService.time("mcp", toolName, Map.of("arguments", args),
                    () -> handler.execute(args));
            return McpToolCallResponse.success(toolName, result, System.currentTimeMillis() - start);
        } catch (RuntimeException exception) {
            observabilityService.record("mcp", toolName, "ERROR", System.currentTimeMillis() - start,
                    Map.of("arguments", args), exception);
            return McpToolCallResponse.failure(toolName, exception.getMessage(), System.currentTimeMillis() - start);
        }
    }
}
