package com.team.rag.agent.skill;

import com.team.rag.service.KnowledgeBaseService;
import com.team.rag.service.RagQAService;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class SearchKnowledgeSkill extends BaseAgentSkill {

    private final RagQAService ragQAService;

    public SearchKnowledgeSkill(RagQAService ragQAService) {
        this.ragQAService = ragQAService;
    }

    @Override
    public String name() {
        return "rag.search_knowledge";
    }

    @Override
    public String description() {
        return "Use the RAG knowledge base to answer a question. Supports category filters and model provider selection.";
    }

    @Override
    public boolean destructive() {
        return false;
    }

    @Override
    public Map<String, Object> inputSchema() {
        return schema(Map.of(
                "query", "string|required",
                "topK", "integer|optional",
                "categoryPath", "string|optional",
                "businessScene", "string|optional",
                "fileType", "string|optional",
                "projectName", "string|optional",
                "department", "string|optional",
                "aiProvider", "dashscope|deepseek|optional"
        ));
    }

    @Override
    public Object execute(Map<String, Object> arguments) {
        return ragQAService.qa(ragQo(arguments, KnowledgeBaseService.SOURCE_RAG));
    }
}
