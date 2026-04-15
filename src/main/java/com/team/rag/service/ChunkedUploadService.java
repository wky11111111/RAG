package com.team.rag.service;

import com.team.rag.bean.ChunkUploadInitRequest;
import com.team.rag.bean.ChunkUploadStatusResponse;
import com.team.rag.bean.DocumentForm;
import com.team.rag.bean.UploadResponse;
import com.team.rag.config.RagProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.stream.IntStream;

@Service
public class ChunkedUploadService {

    private final RagProperties ragProperties;
    private final UploadPolicyService uploadPolicyService;
    private final DocumentService documentService;
    private final ObjectMapper objectMapper;
    private final ConcurrentMap<String, UploadSession> sessions = new ConcurrentHashMap<>();

    public ChunkedUploadService(RagProperties ragProperties,
                                UploadPolicyService uploadPolicyService,
                                DocumentService documentService,
                                ObjectMapper objectMapper) {
        this.ragProperties = ragProperties;
        this.uploadPolicyService = uploadPolicyService;
        this.documentService = documentService;
        this.objectMapper = objectMapper;
    }

    public ChunkUploadStatusResponse initiate(ChunkUploadInitRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("上传初始化参数不能为空");
        }
        if (!StringUtils.hasText(request.getFileName())) {
            throw new IllegalArgumentException("文件名不能为空");
        }
        if (request.getFileSize() <= 0) {
            throw new IllegalArgumentException("文件大小无效");
        }
        long chunkSize = request.getChunkSize() == null
                ? uploadPolicyService.currentPolicy().chunkSizeBytes()
                : request.getChunkSize();
        long expectedTotalChunksLong = (long) Math.ceil(request.getFileSize() / (double) chunkSize);
        if (expectedTotalChunksLong > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("分片数量过多，请调大分片大小后重试");
        }
        int expectedTotalChunks = (int) expectedTotalChunksLong;
        int totalChunks = request.getTotalChunks() == null ? expectedTotalChunks : request.getTotalChunks();
        if (totalChunks <= 0) {
            throw new IllegalArgumentException("分片数量无效");
        }
        if (totalChunks != expectedTotalChunks) {
            throw new IllegalArgumentException("分片数量与文件大小、分片大小不匹配");
        }
        uploadPolicyService.validateChunkedUpload(request.getUserId(), request.getFileSize(), chunkSize);

        String uploadId = UUID.randomUUID().toString();
        Path sessionDir = sessionDir(uploadId);
        try {
            Files.createDirectories(sessionDir);
        } catch (IOException exception) {
            throw new IllegalStateException("创建分片目录失败: " + exception.getMessage(), exception);
        }

        UploadSession session = new UploadSession(
                uploadId,
                request.getFileName(),
                request.getFileSize(),
                request.getFileSha256(),
                chunkSize,
                totalChunks,
                toDocumentForm(request),
                sessionDir
        );
        sessions.put(uploadId, session);
        persistSession(session);
        return status(uploadId);
    }

    public ChunkUploadStatusResponse uploadChunk(String uploadId, int chunkIndex, String chunkSha256, MultipartFile chunk) {
        UploadSession session = getSession(uploadId);
        if (chunkIndex < 0 || chunkIndex >= session.totalChunks()) {
            throw new IllegalArgumentException("分片序号无效");
        }
        if (chunk == null || chunk.isEmpty()) {
            throw new IllegalArgumentException("分片内容不能为空");
        }
        uploadPolicyService.validateChunkSize(chunk.getSize());
        long expectedChunkSize = expectedChunkSize(session, chunkIndex);
        if (chunk.getSize() != expectedChunkSize) {
            throw new IllegalArgumentException("分片 " + chunkIndex + " 大小不匹配，期望 "
                    + expectedChunkSize + "B，实际 " + chunk.getSize() + "B");
        }

        try {
            byte[] bytes = chunk.getBytes();
            if (StringUtils.hasText(chunkSha256)) {
                String actual = sha256(bytes);
                if (!actual.equalsIgnoreCase(chunkSha256)) {
                    throw new IllegalArgumentException("分片 " + chunkIndex + " 校验失败，请重试上传该分片");
                }
            }
            Files.write(chunkPath(session, chunkIndex), bytes);
            return status(uploadId);
        } catch (IOException exception) {
            throw new IllegalStateException("分片保存失败: " + exception.getMessage(), exception);
        }
    }

    public ChunkUploadStatusResponse status(String uploadId) {
        UploadSession session = getSession(uploadId);
        List<Integer> uploaded = uploadedChunks(session);
        return new ChunkUploadStatusResponse(
                session.uploadId(),
                session.fileName(),
                session.fileSize(),
                session.chunkSize(),
                session.totalChunks(),
                uploaded,
                uploaded.size() == session.totalChunks()
        );
    }

    public UploadResponse complete(String uploadId) {
        UploadSession session = getSession(uploadId);
        List<Integer> uploaded = uploadedChunks(session);
        if (uploaded.size() != session.totalChunks()) {
            throw new IllegalArgumentException("分片未上传完整，已上传 " + uploaded.size() + "/" + session.totalChunks());
        }

        Path finalPath = finalPath(session.fileName());
        try {
            Files.createDirectories(finalPath.getParent());
            String actualSha256 = mergeChunks(session, finalPath);
            if (Files.size(finalPath) != session.fileSize()) {
                Files.deleteIfExists(finalPath);
                throw new IllegalArgumentException("合并后文件大小校验失败，请重新上传");
            }
            if (StringUtils.hasText(session.fileSha256()) && !actualSha256.equalsIgnoreCase(session.fileSha256())) {
                Files.deleteIfExists(finalPath);
                throw new IllegalArgumentException("合并后文件完整性校验失败，请重新上传");
            }

            UploadResponse response = documentService.storeExistingFile(
                    finalPath,
                    session.fileName(),
                    session.form(),
                    actualSha256,
                    Files.size(finalPath)
            );
            cleanup(session);
            sessions.remove(uploadId);
            return response;
        } catch (IOException exception) {
            throw new IllegalStateException("合并分片失败: " + exception.getMessage(), exception);
        }
    }

    private String mergeChunks(UploadSession session, Path finalPath) throws IOException {
        MessageDigest digest = sha256Digest();
        try (OutputStream outputStream = Files.newOutputStream(finalPath)) {
            for (int index = 0; index < session.totalChunks(); index++) {
                Path chunkPath = chunkPath(session, index);
                try (InputStream inputStream = Files.newInputStream(chunkPath);
                     DigestInputStream digestInputStream = new DigestInputStream(inputStream, digest)) {
                    digestInputStream.transferTo(outputStream);
                }
            }
        }
        return HexFormat.of().formatHex(digest.digest());
    }

    private List<Integer> uploadedChunks(UploadSession session) {
        return IntStream.range(0, session.totalChunks())
                .filter(index -> Files.exists(chunkPath(session, index)))
                .boxed()
                .toList();
    }

    private UploadSession getSession(String uploadId) {
        UploadSession session = sessions.get(uploadId);
        if (session != null) {
            return session;
        }
        session = loadSession(uploadId);
        if (session != null) {
            sessions.putIfAbsent(uploadId, session);
            return session;
        }
        throw new IllegalArgumentException("上传任务不存在或已过期，请重新初始化上传");
    }

    private void persistSession(UploadSession session) {
        UploadSessionSnapshot snapshot = UploadSessionSnapshot.from(session);
        try {
            Files.createDirectories(session.sessionDir());
            Files.writeString(
                    sessionFile(session.uploadId()),
                    objectMapper.writeValueAsString(snapshot),
                    StandardCharsets.UTF_8
            );
        } catch (IOException exception) {
            throw new IllegalStateException("保存上传会话失败: " + exception.getMessage(), exception);
        }
    }

    private UploadSession loadSession(String uploadId) {
        Path file = sessionFile(uploadId);
        if (!Files.exists(file)) {
            return null;
        }
        try {
            UploadSessionSnapshot snapshot = objectMapper.readValue(file.toFile(), UploadSessionSnapshot.class);
            return snapshot.toSession(sessionDir(snapshot.uploadId()));
        } catch (IOException exception) {
            throw new IllegalArgumentException("上传任务不存在或已过期，请重新初始化上传");
        }
    }

    private long expectedChunkSize(UploadSession session, int chunkIndex) {
        long start = chunkIndex * session.chunkSize();
        long remaining = session.fileSize() - start;
        if (remaining <= 0) {
            throw new IllegalArgumentException("分片序号超出文件范围");
        }
        return Math.min(session.chunkSize(), remaining);
    }

    private Path chunkPath(UploadSession session, int chunkIndex) {
        return session.sessionDir().resolve(chunkIndex + ".part");
    }

    private Path sessionFile(String uploadId) {
        return sessionDir(uploadId).resolve("session.json");
    }

    private Path sessionDir(String uploadId) {
        if (!StringUtils.hasText(uploadId) || !uploadId.matches("[A-Za-z0-9._-]+")) {
            throw new IllegalArgumentException("上传任务 ID 无效");
        }
        Path root = chunkRoot().toAbsolutePath().normalize();
        Path dir = root.resolve(uploadId).normalize();
        if (!dir.startsWith(root)) {
            throw new IllegalArgumentException("上传任务 ID 无效");
        }
        return dir;
    }

    private Path chunkRoot() {
        return Path.of(ragProperties.getStorage().getUploadDir()).resolve(".chunks");
    }

    private Path finalPath(String fileName) {
        String cleaned = StringUtils.cleanPath(fileName).replaceAll("[\\\\/:*?\"<>|\\s]+", "-");
        return Path.of(ragProperties.getStorage().getUploadDir()).resolve(System.currentTimeMillis() + "-" + cleaned);
    }

    private DocumentForm toDocumentForm(ChunkUploadInitRequest request) {
        DocumentForm form = new DocumentForm();
        form.setDocType(request.getDocType());
        form.setDescription(request.getDescription());
        form.setSourceKind(request.getSourceKind());
        form.setCategoryPath(request.getCategoryPath());
        form.setBusinessScene(request.getBusinessScene());
        form.setFileType(request.getFileType());
        form.setProjectName(request.getProjectName());
        form.setDepartment(request.getDepartment());
        form.setUserId(request.getUserId());
        form.setConversationId(request.getConversationId());
        form.setChecksum(request.getFileSha256());
        return form;
    }

    private void cleanup(UploadSession session) {
        try {
            if (Files.exists(session.sessionDir())) {
                try (var paths = Files.walk(session.sessionDir())) {
                    paths.sorted(Comparator.reverseOrder()).forEach(path -> {
                        try {
                            Files.deleteIfExists(path);
                        } catch (IOException ignored) {
                        }
                    });
                }
            }
        } catch (IOException ignored) {
        }
    }

    private String sha256(byte[] bytes) {
        MessageDigest digest = sha256Digest();
        return HexFormat.of().formatHex(digest.digest(bytes));
    }

    private MessageDigest sha256Digest() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 unavailable", exception);
        }
    }

    private record UploadSession(
            String uploadId,
            String fileName,
            long fileSize,
            String fileSha256,
            long chunkSize,
            int totalChunks,
            DocumentForm form,
            Path sessionDir
    ) {
    }

    private record UploadSessionSnapshot(
            String uploadId,
            String fileName,
            long fileSize,
            String fileSha256,
            long chunkSize,
            int totalChunks,
            String docType,
            String description,
            String sourceKind,
            String categoryPath,
            String businessScene,
            String fileType,
            String projectName,
            String department,
            String userId,
            String conversationId,
            String checksum
    ) {
        static UploadSessionSnapshot from(UploadSession session) {
            DocumentForm form = session.form();
            return new UploadSessionSnapshot(
                    session.uploadId(),
                    session.fileName(),
                    session.fileSize(),
                    session.fileSha256(),
                    session.chunkSize(),
                    session.totalChunks(),
                    form.getDocType(),
                    form.getDescription(),
                    form.getSourceKind(),
                    form.getCategoryPath(),
                    form.getBusinessScene(),
                    form.getFileType(),
                    form.getProjectName(),
                    form.getDepartment(),
                    form.getUserId(),
                    form.getConversationId(),
                    form.getChecksum()
            );
        }

        UploadSession toSession(Path sessionDir) {
            DocumentForm form = new DocumentForm();
            form.setDocType(docType);
            form.setDescription(description);
            form.setSourceKind(sourceKind);
            form.setCategoryPath(categoryPath);
            form.setBusinessScene(businessScene);
            form.setFileType(fileType);
            form.setProjectName(projectName);
            form.setDepartment(department);
            form.setUserId(userId);
            form.setConversationId(conversationId);
            form.setChecksum(checksum);
            return new UploadSession(
                    uploadId,
                    fileName,
                    fileSize,
                    fileSha256,
                    chunkSize,
                    totalChunks,
                    form,
                    sessionDir
            );
        }
    }
}
