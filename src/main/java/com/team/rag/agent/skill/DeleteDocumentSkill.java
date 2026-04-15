package com.team.rag.agent.skill;

import com.team.rag.service.DocumentService;
import com.team.rag.service.KnowledgeBaseService;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class DeleteDocumentSkill extends BaseAgentSkill {

    private final DocumentService documentService;

    public DeleteDocumentSkill(DocumentService documentService) {
        this.documentService = documentService;
    }

    @Override
    public String name() {
        return "rag.delete_document";
    }

    @Override
    public String description() {
        return "Delete one RAG document or one long-term memory. Requires confirm=true.";
    }

    @Override
    public boolean destructive() {
        return true;
    }

    @Override
    public Map<String, Object> inputSchema() {
        return schema(Map.of(
                "documentId", "string|required",
                "sourceKind", "RAG|MEMORY|required",
                "confirm", "boolean|required"
        ));
    }

    @Override
    public Object execute(Map<String, Object> arguments) {
        if (!bool(arguments, "confirm")) {
            throw new IllegalArgumentException("delete requires confirm=true");
        }
        String documentId = required(arguments, "documentId");
        String sourceKind = required(arguments, "sourceKind");
        if (KnowledgeBaseService.SOURCE_MEMORY.equalsIgnoreCase(sourceKind)) {
            documentService.deleteManualKnowledge(documentId);
            return Map.of("success", true, "message", "MEMORY document deleted", "documentId", documentId);
        }
        if (KnowledgeBaseService.SOURCE_RAG.equalsIgnoreCase(sourceKind)) {
            documentService.deleteRagKnowledge(documentId);
            return Map.of("success", true, "message", "RAG document deleted", "documentId", documentId);
        }
        throw new IllegalArgumentException("sourceKind must be RAG or MEMORY");
    }
}
