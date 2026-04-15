package com.team.rag.controller;

import com.team.rag.bean.ChunkView;
import com.team.rag.bean.DocumentForm;
import com.team.rag.bean.DocumentSummary;
import com.team.rag.bean.SearchFilter;
import com.team.rag.bean.UploadResponse;
import com.team.rag.service.DocumentService;
import com.team.rag.service.KnowledgeBaseService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/document")
public class DocumentController {

    private final DocumentService documentService;
    private final KnowledgeBaseService knowledgeBaseService;

    public DocumentController(DocumentService documentService, KnowledgeBaseService knowledgeBaseService) {
        this.documentService = documentService;
        this.knowledgeBaseService = knowledgeBaseService;
    }

    @PostMapping("/upload")
    public UploadResponse upload(@ModelAttribute DocumentForm form) {
        return documentService.uploadAndStore(form.getFile(), form);
    }

    @PostMapping("/manual")
    public UploadResponse manual(@Valid @RequestBody DocumentForm form) {
        return documentService.saveManualKnowledge(form);
    }

    @GetMapping("/list")
    public List<DocumentSummary> list(@RequestParam(required = false) String sourceKind,
                                      @RequestParam(required = false) String categoryPath,
                                      @RequestParam(required = false) String businessScene,
                                      @RequestParam(required = false) String fileType,
                                      @RequestParam(required = false) String projectName,
                                      @RequestParam(required = false) String department,
                                      @RequestParam(required = false) String userId,
                                      @RequestParam(required = false) String conversationId) {
        return knowledgeBaseService.listDocuments(new SearchFilter(
                sourceKind,
                categoryPath,
                businessScene,
                fileType,
                projectName,
                department,
                userId,
                conversationId
        ));
    }

    @GetMapping("/memory/list")
    public List<DocumentSummary> listMemories(@RequestParam(required = false) String userId,
                                              @RequestParam(required = false) String conversationId) {
        return knowledgeBaseService.listDocuments(new SearchFilter(
                KnowledgeBaseService.SOURCE_MEMORY,
                null,
                null,
                null,
                null,
                null,
                userId,
                conversationId
        ));
    }

    @DeleteMapping("/{documentId}")
    public Map<String, Object> deleteRagDocument(@PathVariable String documentId) {
        documentService.deleteRagKnowledge(documentId);
        return Map.of("success", true, "message", "知识库文档已删除");
    }

    @GetMapping("/{documentId}/chunks")
    public List<ChunkView> chunks(@PathVariable String documentId) {
        return knowledgeBaseService.getChunksByDocumentUuid(documentId).stream()
                .map(chunk -> new ChunkView(
                        chunk.getChunkUuid(),
                        chunk.getChunkIndex(),
                        chunk.getTokenCount(),
                        chunk.getContent()
                ))
                .toList();
    }

    @PostMapping("/{documentId}/reindex")
    public UploadResponse reindex(@PathVariable String documentId) {
        return documentService.reindexDocument(documentId);
    }

    @PutMapping("/memory/{documentId}")
    public UploadResponse updateMemory(@PathVariable String documentId, @Valid @RequestBody DocumentForm form) {
        return documentService.updateManualKnowledge(documentId, form);
    }

    @DeleteMapping("/memory/{documentId}")
    public Map<String, Object> deleteMemory(@PathVariable String documentId) {
        documentService.deleteManualKnowledge(documentId);
        return Map.of("success", true, "message", "长期记忆已删除");
    }
}
