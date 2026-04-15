package com.team.rag.controller;

import com.team.rag.bean.SystemPromptRequest;
import com.team.rag.bean.SystemPromptResponse;
import com.team.rag.service.AdminGuardService;
import com.team.rag.service.SystemPromptService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/system-prompt")
public class SystemPromptController {

    private final SystemPromptService systemPromptService;
    private final AdminGuardService adminGuardService;

    public SystemPromptController(SystemPromptService systemPromptService,
                                  AdminGuardService adminGuardService) {
        this.systemPromptService = systemPromptService;
        this.adminGuardService = adminGuardService;
    }

    @GetMapping
    public SystemPromptResponse getPrompt() {
        return new SystemPromptResponse(systemPromptService.getPrompt());
    }

    @PutMapping
    public SystemPromptResponse updatePrompt(@RequestHeader(value = "X-Admin-Token", required = false) String adminToken,
                                             @RequestBody SystemPromptRequest request) {
        adminGuardService.requireAdmin(adminToken);
        return new SystemPromptResponse(systemPromptService.updatePrompt(request.getPrompt()));
    }
}
