package com.team.rag.agent.skill;

import com.team.rag.bean.ChunkView;
import com.team.rag.service.KnowledgeBaseService;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class GetDocumentChunksSkill extends BaseAgentSkill {

    private final KnowledgeBaseService knowledgeBaseService;

    public GetDocumentChunksSkill(KnowledgeBaseService knowledgeBaseService) {
        this.knowledgeBaseService = knowledgeBaseService;
    }

    @Override
    public String name() {
        return "rag.get_document_chunks";
    }

    @Override
    public String description() {
        return "Get chunk metadata and text for one document.";
    }

    @Override
    public boolean destructive() {
        return false;
    }

    @Override
    public Map<String, Object> inputSchema() {
        return schema(Map.of("documentId", "string|required"));
    }

    @Override
    public Object execute(Map<String, Object> arguments) {
        return knowledgeBaseService.getChunksByDocumentUuid(required(arguments, "documentId")).stream()
                .map(chunk -> new ChunkView(
                        chunk.getChunkUuid(),
                        chunk.getChunkIndex(),
                        chunk.getTokenCount(),
                        chunk.getContent()
                ))
                .toList();
    }
}
