package com.team.rag.controller;

import com.team.rag.mcp.McpToolCallRequest;
import com.team.rag.mcp.McpToolCallResponse;
import com.team.rag.mcp.McpToolDefinition;
import com.team.rag.mcp.RagMcpToolService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/mcp")
public class McpToolController {

    private final RagMcpToolService toolService;

    public McpToolController(RagMcpToolService toolService) {
        this.toolService = toolService;
    }

    @GetMapping("/tools")
    public List<McpToolDefinition> tools() {
        return toolService.listTools();
    }

    @PostMapping("/call")
    public McpToolCallResponse call(@RequestBody McpToolCallRequest request) {
        return toolService.call(request);
    }
}
