package com.team.rag.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "rag_document")
public class KnowledgeDocumentEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "document_uuid", nullable = false, unique = true, length = 64)
    private String documentUuid;

    @Column(name = "document_name", nullable = false, length = 255)
    private String name;

    @Column(name = "doc_type", nullable = false, length = 128)
    private String docType;

    @Column(name = "source_kind", nullable = false, length = 32)
    private String sourceKind = "RAG";

    @Column(name = "category_path", length = 500)
    private String categoryPath;

    @Column(name = "business_scene", length = 128)
    private String businessScene;

    @Column(name = "file_type", length = 64)
    private String fileType;

    @Column(name = "project_name", length = 128)
    private String projectName;

    @Column(name = "department", length = 128)
    private String department;

    @Column(name = "user_id", length = 128)
    private String userId;

    @Column(name = "conversation_id", length = 128)
    private String conversationId;

    @Column(name = "checksum", length = 128)
    private String checksum;

    @Column(name = "file_size_bytes")
    private Long fileSizeBytes;

    @Column(name = "description_text", length = 1000)
    private String description;

    @Column(name = "source_path", length = 500)
    private String sourcePath;

    @Column(name = "chunk_count", nullable = false)
    private Integer chunkCount;

    @Column(name = "index_status", nullable = false, length = 32)
    private String indexStatus;

    @Column(name = "last_error", length = 2000)
    private String lastError;

    @Lob
    @Column(name = "raw_text", columnDefinition = "LONGTEXT")
    private String rawText;

    @Column(name = "uploaded_at", nullable = false)
    private Instant uploadedAt;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getDocumentUuid() {
        return documentUuid;
    }

    public void setDocumentUuid(String documentUuid) {
        this.documentUuid = documentUuid;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDocType() {
        return docType;
    }

    public void setDocType(String docType) {
        this.docType = docType;
    }

    public String getSourceKind() {
        return sourceKind;
    }

    public void setSourceKind(String sourceKind) {
        this.sourceKind = sourceKind;
    }

    public String getCategoryPath() {
        return categoryPath;
    }

    public void setCategoryPath(String categoryPath) {
        this.categoryPath = categoryPath;
    }

    public String getBusinessScene() {
        return businessScene;
    }

    public void setBusinessScene(String businessScene) {
        this.businessScene = businessScene;
    }

    public String getFileType() {
        return fileType;
    }

    public void setFileType(String fileType) {
        this.fileType = fileType;
    }

    public String getProjectName() {
        return projectName;
    }

    public void setProjectName(String projectName) {
        this.projectName = projectName;
    }

    public String getDepartment() {
        return department;
    }

    public void setDepartment(String department) {
        this.department = department;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public String getConversationId() {
        return conversationId;
    }

    public void setConversationId(String conversationId) {
        this.conversationId = conversationId;
    }

    public String getChecksum() {
        return checksum;
    }

    public void setChecksum(String checksum) {
        this.checksum = checksum;
    }

    public Long getFileSizeBytes() {
        return fileSizeBytes;
    }

    public void setFileSizeBytes(Long fileSizeBytes) {
        this.fileSizeBytes = fileSizeBytes;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getSourcePath() {
        return sourcePath;
    }

    public void setSourcePath(String sourcePath) {
        this.sourcePath = sourcePath;
    }

    public Integer getChunkCount() {
        return chunkCount;
    }

    public void setChunkCount(Integer chunkCount) {
        this.chunkCount = chunkCount;
    }

    public String getIndexStatus() {
        return indexStatus;
    }

    public void setIndexStatus(String indexStatus) {
        this.indexStatus = indexStatus;
    }

    public String getLastError() {
        return lastError;
    }

    public void setLastError(String lastError) {
        this.lastError = lastError;
    }

    public String getRawText() {
        return rawText;
    }

    public void setRawText(String rawText) {
        this.rawText = rawText;
    }

    public Instant getUploadedAt() {
        return uploadedAt;
    }

    public void setUploadedAt(Instant uploadedAt) {
        this.uploadedAt = uploadedAt;
    }
}
