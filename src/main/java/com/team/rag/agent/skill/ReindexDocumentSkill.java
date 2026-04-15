package com.team.rag.agent.skill;

import com.team.rag.service.DocumentService;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class ReindexDocumentSkill extends BaseAgentSkill {

    private final DocumentService documentService;

    public ReindexDocumentSkill(DocumentService documentService) {
        this.documentService = documentService;
    }

    @Override
    public String name() {
        return "rag.reindex_document";
    }

    @Override
    public String description() {
        return "Rebuild chunks and vectors for one existing document.";
    }

    @Override
    public boolean destructive() {
        return true;
    }

    @Override
    public Map<String, Object> inputSchema() {
        return schema(Map.of("documentId", "string|required"));
    }

    @Override
    public Object execute(Map<String, Object> arguments) {
        String documentId = required(arguments, "documentId");
        documentService.reindexDocument(documentId);
        return Map.of("success", true, "message", "Document reindexed", "documentId", documentId);
    }
}
