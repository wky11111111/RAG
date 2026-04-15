package com.team.rag.service;

import com.team.rag.bean.DocumentForm;
import com.team.rag.bean.DocumentMetadata;
import com.team.rag.bean.UploadResponse;
import com.team.rag.bean.UpsertVector;
import com.team.rag.client.DashScopeClient;
import com.team.rag.client.PineconeClient;
import com.team.rag.config.RagProperties;
import com.team.rag.entity.KnowledgeChunkEntity;
import com.team.rag.entity.KnowledgeDocumentEntity;
import com.team.rag.util.SemanticSplitter;
import com.team.rag.util.TextTokenizer;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.parser.AutoDetectParser;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.sax.BodyContentHandler;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;
import org.xml.sax.ContentHandler;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;

@Service
public class DocumentService {

    private static final List<String> TEXT_EXTENSIONS = List.of(
            "txt", "md", "markdown", "csv", "json", "xml", "html", "htm", "log", "yml", "yaml"
    );
    private static final List<String> DOCUMENT_EXTENSIONS = List.of("pdf", "doc", "docx");

    private final SemanticSplitter semanticSplitter;
    private final KnowledgeBaseService knowledgeBaseService;
    private final TextTokenizer textTokenizer;
    private final RagProperties ragProperties;
    private final DashScopeClient dashScopeClient;
    private final PineconeClient pineconeClient;
    private final UploadPolicyService uploadPolicyService;
    private final ObservabilityService observabilityService;
    private final AutoDetectParser parser = new AutoDetectParser();

    public DocumentService(SemanticSplitter semanticSplitter,
                           KnowledgeBaseService knowledgeBaseService,
                           TextTokenizer textTokenizer,
                           RagProperties ragProperties,
                           DashScopeClient dashScopeClient,
                           PineconeClient pineconeClient,
                           UploadPolicyService uploadPolicyService,
                           ObservabilityService observabilityService) {
        this.semanticSplitter = semanticSplitter;
        this.knowledgeBaseService = knowledgeBaseService;
        this.textTokenizer = textTokenizer;
        this.ragProperties = ragProperties;
        this.dashScopeClient = dashScopeClient;
        this.pineconeClient = pineconeClient;
        this.uploadPolicyService = uploadPolicyService;
        this.observabilityService = observabilityService;
    }

    public UploadResponse uploadAndStore(MultipartFile file, DocumentForm form) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("请选择要上传的知识文件");
        }
        String fileName = cleanFileName(file.getOriginalFilename(), "document.txt");
        observabilityService.bind(form.getUserId(), form.getConversationId());
        uploadPolicyService.validateExtension(fileName);
        uploadPolicyService.validateDirectUpload(form.getUserId(), file.getSize());
        try {
            byte[] bytes = file.getBytes();
            validateSafeFile(fileName, bytes);
            String checksum = sha256(bytes);
            UploadResponse duplicated = duplicateResponseIfPresent(KnowledgeBaseService.SOURCE_RAG, form.getUserId(), checksum);
            if (duplicated != null) {
                return duplicated;
            }
            uploadPolicyService.validateUserTotalStorage(
                    form.getUserId(),
                    knowledgeBaseService.userStorageBytes(form.getUserId()),
                    file.getSize()
            );
            String text = observabilityService.time("document", "text_extract", Map.of("fileName", fileName, "fileSize", file.getSize()),
                    () -> extractText(fileName, bytes));
            Path savedPath = observabilityService.time("document", "file_save", Map.of("fileName", fileName),
                    () -> writeUploadedBytes(fileName, bytes));
            UploadResponse response = storeKnowledge(
                    fileName,
                    form.getDocType(),
                    form.getDescription(),
                    text,
                    savedPath,
                    metadataFromForm(form, KnowledgeBaseService.SOURCE_RAG, extensionOf(fileName), checksum, file.getSize()),
                    null
            );
            uploadPolicyService.recordUsage(form.getUserId(), file.getSize());
            return response;
        } catch (IOException exception) {
            throw new IllegalStateException("文档保存失败: " + exception.getMessage(), exception);
        }
    }

    public UploadResponse storeExistingFile(Path filePath,
                                            String fileName,
                                            DocumentForm form,
                                            String checksum,
                                            long fileSizeBytes) {
        try {
            String effectiveFileName = cleanFileName(fileName, "document");
            observabilityService.bind(form.getUserId(), form.getConversationId());
            uploadPolicyService.validateExtension(effectiveFileName);
            validateSafeFileHeader(effectiveFileName, filePath, fileSizeBytes);
            String actualChecksum = StringUtils.hasText(checksum) ? checksum : sha256(filePath);
            UploadResponse duplicated = duplicateResponseIfPresent(KnowledgeBaseService.SOURCE_RAG, form.getUserId(), actualChecksum);
            if (duplicated != null) {
                Files.deleteIfExists(filePath);
                return duplicated;
            }
            uploadPolicyService.validateUserTotalStorage(
                    form.getUserId(),
                    knowledgeBaseService.userStorageBytes(form.getUserId()),
                    fileSizeBytes
            );
            String text = observabilityService.time("document", "text_extract", Map.of("fileName", effectiveFileName, "fileSize", fileSizeBytes),
                    () -> {
                        try {
                            return extractText(effectiveFileName, filePath);
                        } catch (IOException exception) {
                            throw new IllegalStateException("文档解析失败: " + exception.getMessage(), exception);
                        }
                    });
            UploadResponse response = storeKnowledge(
                    effectiveFileName,
                    form.getDocType(),
                    form.getDescription(),
                    text,
                    filePath,
                    metadataFromForm(form, KnowledgeBaseService.SOURCE_RAG, extensionOf(effectiveFileName), actualChecksum, fileSizeBytes),
                    null
            );
            uploadPolicyService.recordUsage(form.getUserId(), fileSizeBytes);
            return response;
        } catch (IOException exception) {
            throw new IllegalStateException("文档保存失败: " + exception.getMessage(), exception);
        }
    }

    public UploadResponse saveManualKnowledge(DocumentForm form) {
        if (!StringUtils.hasText(form.getContent())) {
            throw new IllegalArgumentException("长期记忆内容不能为空");
        }
        String normalized = normalizeExtractedText(form.getContent());
        if (normalized.isBlank()) {
            throw new IllegalArgumentException("长期记忆内容不能为空");
        }

        String effectiveTitle = StringUtils.hasText(form.getTitle()) ? form.getTitle().trim() : "manual-memory";
        String fileName = effectiveTitle.endsWith(".md") || effectiveTitle.endsWith(".txt")
                ? effectiveTitle
                : effectiveTitle + ".md";
        byte[] bytes = normalized.getBytes(StandardCharsets.UTF_8);

        try {
            Path savedPath = saveUploadedBytes(fileName, bytes);
            return storeKnowledge(
                    fileName,
                    form.getDocType(),
                    form.getDescription(),
                    normalized,
                    savedPath,
                    metadataFromForm(form, KnowledgeBaseService.SOURCE_MEMORY, "manual", sha256(bytes), (long) bytes.length),
                    null
            );
        } catch (IOException exception) {
            throw new IllegalStateException("长期记忆保存失败: " + exception.getMessage(), exception);
        }
    }

    public UploadResponse updateManualKnowledge(String documentId, DocumentForm form) {
        KnowledgeDocumentEntity existing = knowledgeBaseService.findDocument(documentId);
        if (!KnowledgeBaseService.SOURCE_MEMORY.equalsIgnoreCase(existing.getSourceKind())) {
            throw new IllegalArgumentException("只能通过长期记忆接口更新 MEMORY 类型内容");
        }
        if (!StringUtils.hasText(form.getContent())) {
            throw new IllegalArgumentException("长期记忆内容不能为空");
        }

        List<String> oldVectorIds = knowledgeBaseService.getChunksByDocumentUuid(documentId).stream()
                .map(KnowledgeChunkEntity::getChunkUuid)
                .toList();
        if (!oldVectorIds.isEmpty()) {
            pineconeClient.deleteIds(oldVectorIds, KnowledgeBaseService.SOURCE_MEMORY);
        }

        String normalized = normalizeExtractedText(form.getContent());
        String title = StringUtils.hasText(form.getTitle()) ? form.getTitle().trim() : existing.getName();
        String fileName = title.endsWith(".md") || title.endsWith(".txt") ? title : title + ".md";
        byte[] bytes = normalized.getBytes(StandardCharsets.UTF_8);

        try {
            Path savedPath = saveUploadedBytes(fileName, bytes);
            return storeKnowledge(
                    fileName,
                    form.getDocType(),
                    form.getDescription(),
                    normalized,
                    savedPath,
                    metadataFromForm(form, KnowledgeBaseService.SOURCE_MEMORY, "manual", sha256(bytes), (long) bytes.length),
                    documentId
            );
        } catch (IOException exception) {
            throw new IllegalStateException("长期记忆更新失败: " + exception.getMessage(), exception);
        }
    }

    public void deleteManualKnowledge(String documentId) {
        KnowledgeDocumentEntity existing = knowledgeBaseService.findDocument(documentId);
        if (!KnowledgeBaseService.SOURCE_MEMORY.equalsIgnoreCase(existing.getSourceKind())) {
            throw new IllegalArgumentException("只能通过长期记忆接口删除 MEMORY 类型内容");
        }
        deleteKnowledgeDocument(existing);
    }

    public void deleteRagKnowledge(String documentId) {
        KnowledgeDocumentEntity existing = knowledgeBaseService.findDocument(documentId);
        if (!KnowledgeBaseService.SOURCE_RAG.equalsIgnoreCase(existing.getSourceKind())) {
            throw new IllegalArgumentException("只能通过知识库接口删除 RAG 类型文档");
        }
        deleteKnowledgeDocument(existing);
    }

    public UploadResponse reindexDocument(String documentId) {
        KnowledgeDocumentEntity existing = knowledgeBaseService.findDocument(documentId);
        List<String> oldVectorIds = knowledgeBaseService.getChunksByDocumentUuid(documentId).stream()
                .map(KnowledgeChunkEntity::getChunkUuid)
                .toList();
        if (!oldVectorIds.isEmpty()) {
            pineconeClient.deleteIds(oldVectorIds, existing.getSourceKind());
        }

        DocumentForm form = new DocumentForm();
        form.setDocType(existing.getDocType());
        form.setDescription(existing.getDescription());
        form.setSourceKind(existing.getSourceKind());
        form.setCategoryPath(existing.getCategoryPath());
        form.setBusinessScene(existing.getBusinessScene());
        form.setFileType(existing.getFileType());
        form.setProjectName(existing.getProjectName());
        form.setDepartment(existing.getDepartment());
        form.setUserId(existing.getUserId());
        form.setConversationId(existing.getConversationId());
        form.setChecksum(existing.getChecksum());

        try {
            return storeKnowledge(
                    existing.getName(),
                    existing.getDocType(),
                    existing.getDescription(),
                    existing.getRawText(),
                    Path.of(existing.getSourcePath()),
                    metadataFromForm(form, existing.getSourceKind(), existing.getFileType(), existing.getChecksum(), existing.getFileSizeBytes()),
                    existing.getDocumentUuid()
            );
        } catch (IOException exception) {
            throw new IllegalStateException("文档重新索引失败: " + exception.getMessage(), exception);
        }
    }

    private void deleteKnowledgeDocument(KnowledgeDocumentEntity existing) {
        List<String> vectorIds = knowledgeBaseService.getChunksByDocumentUuid(existing.getDocumentUuid()).stream()
                .map(KnowledgeChunkEntity::getChunkUuid)
                .toList();
        pineconeClient.deleteIds(vectorIds, existing.getSourceKind());
        knowledgeBaseService.deleteDocument(existing.getDocumentUuid());
        deleteStoredFile(existing.getSourcePath());
    }

    private void deleteStoredFile(String sourcePath) {
        if (!StringUtils.hasText(sourcePath)) {
            return;
        }
        try {
            Path uploadRoot = Path.of(ragProperties.getStorage().getUploadDir()).toAbsolutePath().normalize();
            Path target = Path.of(sourcePath).toAbsolutePath().normalize();
            if (target.startsWith(uploadRoot)) {
                Files.deleteIfExists(target);
            }
        } catch (IOException ignored) {
        }
    }

    private UploadResponse storeKnowledge(String fileName,
                                          String docType,
                                          String description,
                                          String text,
                                          Path savedPath,
                                          DocumentMetadata metadata,
                                          String existingDocumentId) throws IOException {
        List<String> chunks = observabilityService.time("document", "text_split", Map.of(
                "fileName", fileName,
                "sourceKind", metadata.sourceKind()
        ), () -> semanticSplitter.split(text));
        if (chunks.isEmpty()) {
            throw new IllegalArgumentException("知识内容切块失败，请检查文件是否包含可读取文本");
        }

        KnowledgeDocumentEntity document = existingDocumentId == null
                ? knowledgeBaseService.createDocument(
                fileName,
                docType,
                description,
                savedPath.toString(),
                text,
                chunks,
                chunkText -> textTokenizer.tokenize(chunkText).size(),
                metadata
        )
                : knowledgeBaseService.replaceDocument(
                existingDocumentId,
                fileName,
                docType,
                description,
                savedPath.toString(),
                text,
                chunks,
                chunkText -> textTokenizer.tokenize(chunkText).size(),
                metadata
        );

        try {
            syncToVectorStore(document, chunks);
            knowledgeBaseService.markReady(document.getDocumentUuid());
            return new UploadResponse(
                    true,
                    "知识内容已写入知识库并完成索引",
                    document.getDocumentUuid(),
                    chunks.size(),
                    "READY",
                    (int) knowledgeBaseService.countDocuments()
            );
        } catch (RuntimeException exception) {
            knowledgeBaseService.markFailed(document.getDocumentUuid(), exception.getMessage());
            throw exception;
        }
    }

    private void syncToVectorStore(KnowledgeDocumentEntity document, List<String> chunks) {
        List<List<Float>> embeddings = observabilityService.time("vector", "embedding", Map.of(
                "documentId", document.getDocumentUuid(),
                "chunkCount", chunks.size(),
                "sourceKind", document.getSourceKind()
        ), () -> dashScopeClient.embedAll(chunks));
        if (embeddings.size() != chunks.size()) {
            throw new IllegalStateException("Embedding result count does not match chunk count");
        }

        List<UpsertVector> vectors = java.util.stream.IntStream.range(0, chunks.size())
                .mapToObj(index -> {
                    Map<String, Object> metadata = new HashMap<>();
                    metadata.put("documentId", document.getDocumentUuid());
                    metadata.put("chunkId", document.getDocumentUuid() + "-chunk-" + index);
                    metadata.put("documentName", document.getName());
                    metadata.put("docType", document.getDocType());
                    metadata.put("sourceKind", document.getSourceKind());
                    metadata.put("categoryPath", nullToEmpty(document.getCategoryPath()));
                    metadata.put("businessScene", nullToEmpty(document.getBusinessScene()));
                    metadata.put("fileType", nullToEmpty(document.getFileType()));
                    metadata.put("projectName", nullToEmpty(document.getProjectName()));
                    metadata.put("department", nullToEmpty(document.getDepartment()));
                    metadata.put("userId", nullToEmpty(document.getUserId()));
                    metadata.put("conversationId", nullToEmpty(document.getConversationId()));
                    return new UpsertVector(
                            document.getDocumentUuid() + "-chunk-" + index,
                            embeddings.get(index),
                            metadata
                    );
                })
                .toList();
        observabilityService.time("vector", "pinecone_upsert", Map.of(
                "documentId", document.getDocumentUuid(),
                "vectorCount", vectors.size(),
                "sourceKind", document.getSourceKind()
        ), () -> {
            pineconeClient.upsert(vectors);
            return true;
        });
    }

    private String extractText(String fileName, byte[] content) {
        String extension = extensionOf(fileName);
        if (TEXT_EXTENSIONS.contains(extension)) {
            return normalizePlainText(extension, new String(content, StandardCharsets.UTF_8));
        }
        if (DOCUMENT_EXTENSIONS.contains(extension)) {
            return extractDocumentText(fileName, new ByteArrayInputStream(content));
        }

        throw new IllegalArgumentException("当前支持 txt、md、html、json、csv、xml、log、yaml、pdf、doc、docx 等知识文件");
    }

    private String extractText(String fileName, Path path) throws IOException {
        String extension = extensionOf(fileName);
        if (TEXT_EXTENSIONS.contains(extension)) {
            return normalizePlainText(extension, Files.readString(path, StandardCharsets.UTF_8));
        }
        if (DOCUMENT_EXTENSIONS.contains(extension)) {
            try (InputStream inputStream = Files.newInputStream(path)) {
                return extractDocumentText(fileName, inputStream);
            }
        }
        throw new IllegalArgumentException("当前支持 txt、md、html、json、csv、xml、log、yaml、pdf、doc、docx 等知识文件");
    }

    private String extractDocumentText(String fileName, InputStream inputStream) {
        try {
            ContentHandler handler = new BodyContentHandler(-1);
            Metadata metadata = new Metadata();
            metadata.set(TikaCoreProperties.RESOURCE_NAME_KEY, fileName);
            parser.parse(inputStream, handler, metadata, new ParseContext());
            String normalized = normalizeExtractedText(handler.toString());
            if (normalized.isBlank()) {
                throw new IllegalArgumentException("上传的文档没有解析出可用文本内容。如果是扫描版 PDF，需要先做 OCR。");
            }
            return normalized;
        } catch (IllegalArgumentException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new IllegalArgumentException("文档解析失败，请确认 " + fileName + " 不是扫描件、加密文件或损坏文件", exception);
        }
    }

    private String normalizePlainText(String extension, String text) {
        String normalized = normalizeExtractedText(text);
        if ("html".equals(extension) || "htm".equals(extension)) {
            normalized = normalized.replaceAll("<[^>]+>", " ");
        }
        if ("md".equals(extension) || "markdown".equals(extension)) {
            normalized = normalized
                    .replaceAll("(?m)^#{1,6}\\s*", "")
                    .replaceAll("(?m)^>\\s*", "")
                    .replaceAll("(?m)^[-*+]\\s+", "")
                    .replace("**", "")
                    .replace("__", "")
                    .replace("`", "");
        }
        normalized = normalizeExtractedText(normalized);
        if (normalized.isBlank()) {
            throw new IllegalArgumentException("上传的文件没有可用文本内容");
        }
        return normalized;
    }

    private Path saveUploadedBytes(String fileName, byte[] bytes) throws IOException {
        Path uploadDir = Path.of(ragProperties.getStorage().getUploadDir());
        Files.createDirectories(uploadDir);
        Path savedPath = uploadDir.resolve(System.currentTimeMillis() + "-" + fileName.replaceAll("[\\\\/:*?\"<>|\\s]+", "-"));
        Files.write(savedPath, bytes);
        return savedPath;
    }

    private Path writeUploadedBytes(String fileName, byte[] bytes) {
        try {
            return saveUploadedBytes(fileName, bytes);
        } catch (IOException exception) {
            throw new IllegalStateException("文档保存失败: " + exception.getMessage(), exception);
        }
    }

    private DocumentMetadata metadataFromForm(DocumentForm form,
                                              String defaultSourceKind,
                                              String detectedFileType,
                                              String checksum,
                                              Long fileSizeBytes) {
        return new DocumentMetadata(
                StringUtils.hasText(form.getSourceKind()) ? form.getSourceKind() : defaultSourceKind,
                form.getCategoryPath(),
                form.getBusinessScene(),
                StringUtils.hasText(form.getFileType()) ? form.getFileType() : detectedFileType,
                form.getProjectName(),
                form.getDepartment(),
                form.getUserId(),
                form.getConversationId(),
                StringUtils.hasText(form.getChecksum()) ? form.getChecksum() : checksum,
                fileSizeBytes
        );
    }

    private String normalizeExtractedText(String text) {
        return text == null ? "" : text
                .replace("\r\n", "\n")
                .replace('\r', '\n')
                .replace("\uFEFF", "")
                .replaceAll("[\\t\\x0B\\f]+", " ")
                .replaceAll("\\n{3,}", "\n\n")
                .trim();
    }

    private String extensionOf(String fileName) {
        int index = fileName.lastIndexOf('.');
        if (index < 0 || index == fileName.length() - 1) {
            return "txt";
        }
        return fileName.substring(index + 1).toLowerCase();
    }

    private String cleanFileName(String fileName, String fallback) {
        return StringUtils.cleanPath(StringUtils.hasText(fileName) ? fileName : fallback);
    }

    private String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    private String sha256(byte[] bytes) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(bytes));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 unavailable", exception);
        }
    }

    private String sha256(Path path) throws IOException {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] buffer = new byte[1024 * 1024];
            try (InputStream inputStream = Files.newInputStream(path)) {
                int read;
                while ((read = inputStream.read(buffer)) != -1) {
                    digest.update(buffer, 0, read);
                }
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 unavailable", exception);
        }
    }

    private UploadResponse duplicateResponseIfPresent(String sourceKind, String userId, String checksum) {
        KnowledgeDocumentEntity duplicated = knowledgeBaseService.findReadyDuplicate(sourceKind, userId, checksum);
        if (duplicated == null) {
            return null;
        }
        observabilityService.record("document", "deduplicate", "SUCCESS", 0, Map.of(
                "sourceKind", sourceKind,
                "documentId", duplicated.getDocumentUuid(),
                "checksum", checksum
        ), null);
        return new UploadResponse(
                true,
                "文件内容已存在，已复用现有知识库文档",
                duplicated.getDocumentUuid(),
                duplicated.getChunkCount(),
                duplicated.getIndexStatus(),
                (int) knowledgeBaseService.countDocuments()
        );
    }

    private void validateSafeFile(String fileName, byte[] bytes) {
        if (bytes.length >= 2 && bytes[0] == 'M' && bytes[1] == 'Z') {
            throw new IllegalArgumentException("检测到 Windows 可执行文件特征，禁止上传");
        }
        if (bytes.length >= 4 && bytes[0] == 0x7f && bytes[1] == 'E' && bytes[2] == 'L' && bytes[3] == 'F') {
            throw new IllegalArgumentException("检测到 Linux 可执行文件特征，禁止上传");
        }
        String extension = extensionOf(fileName);
        if (List.of("jsp", "php", "asp", "aspx", "exe", "dll", "bat", "cmd", "sh").contains(extension)) {
            throw new IllegalArgumentException("检测到高风险文件类型，禁止上传");
        }
        observabilityService.record("security", "malware_scan", "SUCCESS", 0, Map.of(
                "fileName", fileName,
                "fileSize", bytes.length
        ), null);
    }
    private void validateSafeFileHeader(String fileName, Path path, long fileSizeBytes) throws IOException {
        byte[] header;
        try (InputStream inputStream = Files.newInputStream(path)) {
            header = inputStream.readNBytes(16);
        }
        if (header.length >= 2 && header[0] == 'M' && header[1] == 'Z') {
            throw new IllegalArgumentException("检测到 Windows 可执行文件特征，禁止上传");
        }
        if (header.length >= 4 && header[0] == 0x7f && header[1] == 'E' && header[2] == 'L' && header[3] == 'F') {
            throw new IllegalArgumentException("检测到 Linux 可执行文件特征，禁止上传");
        }
        String extension = extensionOf(fileName);
        if (List.of("jsp", "php", "asp", "aspx", "exe", "dll", "bat", "cmd", "sh").contains(extension)) {
            throw new IllegalArgumentException("检测到高风险文件类型，禁止上传");
        }
        observabilityService.record("security", "malware_scan", "SUCCESS", 0, Map.of(
                "fileName", fileName,
                "fileSize", fileSizeBytes
        ), null);
    }
}
