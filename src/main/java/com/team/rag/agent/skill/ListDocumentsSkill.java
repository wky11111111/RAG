package com.team.rag.agent.skill;

import com.team.rag.service.KnowledgeBaseService;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class ListDocumentsSkill extends BaseAgentSkill {

    private final KnowledgeBaseService knowledgeBaseService;

    public ListDocumentsSkill(KnowledgeBaseService knowledgeBaseService) {
        this.knowledgeBaseService = knowledgeBaseService;
    }

    @Override
    public String name() {
        return "rag.list_documents";
    }

    @Override
    public String description() {
        return "List RAG documents or long-term memories with metadata filters.";
    }

    @Override
    public boolean destructive() {
        return false;
    }

    @Override
    public Map<String, Object> inputSchema() {
        return schema(Map.of(
                "sourceKind", "RAG|MEMORY|ALL|optional",
                "categoryPath", "string|optional",
                "businessScene", "string|optional",
                "fileType", "string|optional",
                "projectName", "string|optional",
                "department", "string|optional",
                "userId", "string|optional",
                "conversationId", "string|optional"
        ));
    }

    @Override
    public Object execute(Map<String, Object> arguments) {
        return knowledgeBaseService.listDocuments(searchFilter(arguments, null));
    }
}
