package com.team.rag.agent.skill;

import com.team.rag.service.KnowledgeBaseService;
import com.team.rag.service.RagQAService;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class SearchMemorySkill extends BaseAgentSkill {

    private final RagQAService ragQAService;

    public SearchMemorySkill(RagQAService ragQAService) {
        this.ragQAService = ragQAService;
    }

    @Override
    public String name() {
        return "rag.search_memory";
    }

    @Override
    public String description() {
        return "Use long-term memory to answer a question. Memory retrieval is isolated by userId and optional conversationId.";
    }

    @Override
    public boolean destructive() {
        return false;
    }

    @Override
    public Map<String, Object> inputSchema() {
        return schema(Map.of(
                "query", "string|required",
                "userId", "string|required",
                "conversationId", "string|optional",
                "topK", "integer|optional",
                "aiProvider", "dashscope|deepseek|optional"
        ));
    }

    @Override
    public Object execute(Map<String, Object> arguments) {
        return ragQAService.qa(ragQo(arguments, KnowledgeBaseService.SOURCE_MEMORY));
    }
}
