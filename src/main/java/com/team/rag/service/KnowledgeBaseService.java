package com.team.rag.service;

import com.team.rag.bean.DocumentMetadata;
import com.team.rag.bean.DocumentStatus;
import com.team.rag.bean.DocumentSummary;
import com.team.rag.bean.SearchFilter;
import com.team.rag.entity.KnowledgeChunkEntity;
import com.team.rag.entity.KnowledgeDocumentEntity;
import com.team.rag.repository.KnowledgeChunkRepository;
import com.team.rag.repository.KnowledgeDocumentRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class KnowledgeBaseService {

    public static final String SOURCE_RAG = "RAG";
    public static final String SOURCE_MEMORY = "MEMORY";

    private final KnowledgeDocumentRepository documentRepository;
    private final KnowledgeChunkRepository chunkRepository;

    public KnowledgeBaseService(KnowledgeDocumentRepository documentRepository,
                                KnowledgeChunkRepository chunkRepository) {
        this.documentRepository = documentRepository;
        this.chunkRepository = chunkRepository;
    }

    @Transactional
    public KnowledgeDocumentEntity createDocument(String fileName,
                                                  String docType,
                                                  String description,
                                                  String sourcePath,
                                                  String rawText,
                                                  List<String> chunkTexts,
                                                  Function<String, Integer> tokenCounter,
                                                  DocumentMetadata metadata) {
        KnowledgeDocumentEntity document = new KnowledgeDocumentEntity();
        document.setDocumentUuid(UUID.randomUUID().toString());
        document.setName(fileName);
        document.setDocType(normalize(docType, "未分类"));
        document.setDescription(normalize(description, ""));
        document.setSourcePath(sourcePath);
        document.setRawText(rawText);
        document.setChunkCount(chunkTexts.size());
        document.setIndexStatus(DocumentStatus.PROCESSING);
        document.setUploadedAt(Instant.now());
        applyMetadata(document, metadata);
        documentRepository.save(document);

        saveChunks(document, chunkTexts, tokenCounter);
        return document;
    }

    @Transactional
    public KnowledgeDocumentEntity replaceDocument(String documentUuid,
                                                   String fileName,
                                                   String docType,
                                                   String description,
                                                   String sourcePath,
                                                   String rawText,
                                                   List<String> chunkTexts,
                                                   Function<String, Integer> tokenCounter,
                                                   DocumentMetadata metadata) {
        KnowledgeDocumentEntity document = findDocument(documentUuid);
        chunkRepository.deleteByDocumentDocumentUuid(documentUuid);
        document.setName(fileName);
        document.setDocType(normalize(docType, "未分类"));
        document.setDescription(normalize(description, ""));
        document.setSourcePath(sourcePath);
        document.setRawText(rawText);
        document.setChunkCount(chunkTexts.size());
        document.setIndexStatus(DocumentStatus.PROCESSING);
        document.setLastError(null);
        document.setUploadedAt(Instant.now());
        applyMetadata(document, metadata);
        saveChunks(document, chunkTexts, tokenCounter);
        return document;
    }

    @Transactional
    public void deleteDocument(String documentUuid) {
        KnowledgeDocumentEntity document = findDocument(documentUuid);
        chunkRepository.deleteByDocumentDocumentUuid(documentUuid);
        documentRepository.delete(document);
    }

    @Transactional
    public void markReady(String documentUuid) {
        KnowledgeDocumentEntity document = findDocument(documentUuid);
        document.setIndexStatus(DocumentStatus.READY);
        document.setLastError(null);
    }

    @Transactional
    public void markFailed(String documentUuid, String errorMessage) {
        KnowledgeDocumentEntity document = findDocument(documentUuid);
        document.setIndexStatus(DocumentStatus.FAILED);
        document.setLastError(errorMessage);
    }

    public KnowledgeDocumentEntity findDocument(String documentUuid) {
        return documentRepository.findByDocumentUuid(documentUuid)
                .orElseThrow(() -> new IllegalArgumentException("文档不存在"));
    }

    public KnowledgeDocumentEntity findReadyDuplicate(String sourceKind, String userId, String checksum) {
        if (!StringUtils.hasText(checksum)) {
            return null;
        }
        return documentRepository.findFirstBySourceKindAndUserIdAndChecksumAndIndexStatusOrderByUploadedAtDesc(
                        normalize(sourceKind, SOURCE_RAG).toUpperCase(),
                        normalize(userId, "anonymous"),
                        checksum,
                        DocumentStatus.READY
                )
                .orElse(null);
    }

    public long userStorageBytes(String userId) {
        Long total = documentRepository.sumFileSizeByUserId(normalize(userId, "anonymous"));
        return total == null ? 0L : total;
    }

    public List<DocumentSummary> listDocuments() {
        return listDocuments(SearchFilter.empty());
    }

    public List<DocumentSummary> listDocuments(SearchFilter filter) {
        return documentRepository.findAll().stream()
                .filter(document -> filter == null || filter.matches(document))
                .sorted((left, right) -> right.getUploadedAt().compareTo(left.getUploadedAt()))
                .map(this::toSummary)
                .toList();
    }

    public List<KnowledgeChunkEntity> getAllChunks() {
        return chunkRepository.findAllByOrderByIdAsc();
    }

    public List<KnowledgeChunkEntity> getChunks(SearchFilter filter) {
        return getAllChunks().stream()
                .filter(chunk -> filter == null || filter.matches(chunk.getDocument()))
                .toList();
    }

    public List<KnowledgeChunkEntity> getChunksByDocumentUuid(String documentUuid) {
        return chunkRepository.findByDocumentDocumentUuidOrderByChunkIndexAsc(documentUuid);
    }

    public Map<String, KnowledgeChunkEntity> findChunksByUuid(List<String> chunkUuids) {
        return chunkRepository.findByChunkUuidIn(chunkUuids).stream()
                .collect(Collectors.toMap(KnowledgeChunkEntity::getChunkUuid, Function.identity()));
    }

    public long countDocuments() {
        return documentRepository.count();
    }

    private void saveChunks(KnowledgeDocumentEntity document,
                            List<String> chunkTexts,
                            Function<String, Integer> tokenCounter) {
        for (int i = 0; i < chunkTexts.size(); i++) {
            KnowledgeChunkEntity chunk = new KnowledgeChunkEntity();
            chunk.setChunkUuid(document.getDocumentUuid() + "-chunk-" + i);
            chunk.setDocument(document);
            chunk.setChunkIndex(i);
            chunk.setTokenCount(tokenCounter.apply(chunkTexts.get(i)));
            chunk.setContent(chunkTexts.get(i));
            chunkRepository.save(chunk);
        }
    }

    private void applyMetadata(KnowledgeDocumentEntity document, DocumentMetadata metadata) {
        DocumentMetadata effective = metadata == null
                ? new DocumentMetadata(SOURCE_RAG, "", "", "", "", "", "anonymous", "", "", null)
                : metadata;
        document.setSourceKind(normalize(effective.sourceKind(), SOURCE_RAG).toUpperCase());
        document.setCategoryPath(normalize(effective.categoryPath(), ""));
        document.setBusinessScene(normalize(effective.businessScene(), ""));
        document.setFileType(normalize(effective.fileType(), ""));
        document.setProjectName(normalize(effective.projectName(), ""));
        document.setDepartment(normalize(effective.department(), ""));
        document.setUserId(normalize(effective.userId(), "anonymous"));
        document.setConversationId(normalize(effective.conversationId(), ""));
        document.setChecksum(normalize(effective.checksum(), ""));
        document.setFileSizeBytes(effective.fileSizeBytes());
    }

    private DocumentSummary toSummary(KnowledgeDocumentEntity document) {
        return new DocumentSummary(
                document.getDocumentUuid(),
                document.getName(),
                document.getDocType(),
                document.getDescription(),
                document.getChunkCount(),
                document.getIndexStatus(),
                document.getLastError(),
                document.getUploadedAt(),
                document.getSourceKind(),
                document.getCategoryPath(),
                document.getBusinessScene(),
                document.getFileType(),
                document.getProjectName(),
                document.getDepartment(),
                document.getUserId(),
                document.getConversationId(),
                document.getFileSizeBytes()
        );
    }

    private String normalize(String value, String fallback) {
        return StringUtils.hasText(value) ? value.trim() : fallback;
    }
}
