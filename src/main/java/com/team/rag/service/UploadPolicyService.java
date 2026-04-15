package com.team.rag.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.team.rag.bean.UploadPolicy;
import com.team.rag.config.RagProperties;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

@Service
public class UploadPolicyService {

    private static final String POLICY_KEY = "rag:upload:policy";

    private final AtomicReference<UploadPolicy> policy;
    private final StringRedisTemplate stringRedisTemplate;
    private final ObjectMapper objectMapper;
    private final Map<String, Long> localUsage = new ConcurrentHashMap<>();

    public UploadPolicyService(RagProperties ragProperties,
                               StringRedisTemplate stringRedisTemplate,
                               ObjectMapper objectMapper) {
        this.stringRedisTemplate = stringRedisTemplate;
        this.objectMapper = objectMapper;
        this.policy = new AtomicReference<>(new UploadPolicy(
                ragProperties.getUpload().getDirectMaxFileSize().toBytes(),
                ragProperties.getUpload().getChunkSize().toBytes(),
                ragProperties.getUpload().getMaxChunkSize().toBytes(),
                ragProperties.getUpload().getMaxChunkedFileSize().toBytes(),
                ragProperties.getUpload().getDailyTotalQuota().toBytes(),
                ragProperties.getUpload().getUserDailyQuota().toBytes(),
                ragProperties.getUpload().getUserTotalStorageQuota().toBytes(),
                normalizeExtensions(ragProperties.getUpload().getAllowedExtensions())
        ));
    }

    public UploadPolicy currentPolicy() {
        loadPersistedPolicy();
        return policy.get();
    }

    public UploadPolicy updatePolicy(UploadPolicy next) {
        UploadPolicy normalized = normalizePolicy(next);
        validatePolicy(normalized);
        policy.set(normalized);
        persistPolicy(normalized);
        return normalized;
    }

    public void validateDirectUpload(String userId, long fileSize) {
        UploadPolicy current = currentPolicy();
        if (fileSize <= 0) {
            throw new IllegalArgumentException("文件大小无效");
        }
        if (fileSize > current.directMaxFileBytes()) {
            throw new IllegalArgumentException("普通上传单文件最大支持 " + humanSize(current.directMaxFileBytes()) + "，大文件请使用分片上传");
        }
        validateQuota(userId, fileSize);
    }

    public void validateChunkedUpload(String userId, long fileSize, long chunkSize) {
        UploadPolicy current = currentPolicy();
        if (fileSize <= 0) {
            throw new IllegalArgumentException("文件大小无效");
        }
        if (fileSize > current.maxChunkedFileBytes()) {
            throw new IllegalArgumentException("分片上传单文件最大支持 " + humanSize(current.maxChunkedFileBytes()));
        }
        if (chunkSize <= 0 || chunkSize > current.maxChunkSizeBytes()) {
            throw new IllegalArgumentException("分片大小必须在 1B 到 " + humanSize(current.maxChunkSizeBytes()) + " 之间");
        }
        validateQuota(userId, fileSize);
    }

    public void validateChunkSize(long chunkSize) {
        UploadPolicy current = currentPolicy();
        if (chunkSize <= 0 || chunkSize > current.maxChunkSizeBytes()) {
            throw new IllegalArgumentException("单个分片最大支持 " + humanSize(current.maxChunkSizeBytes()));
        }
    }

    public void validateExtension(String fileName) {
        String extension = extensionOf(fileName);
        List<String> allowed = currentPolicy().allowedExtensions();
        if (allowed == null || allowed.isEmpty()) {
            return;
        }
        if (!allowed.contains(extension)) {
            throw new IllegalArgumentException("当前不允许上传 ." + extension + " 文件，允许类型：" + String.join(", ", allowed));
        }
    }

    public void validateUserTotalStorage(String userId, long currentStorageBytes, long incomingBytes) {
        UploadPolicy current = currentPolicy();
        if (currentStorageBytes + incomingBytes > current.userTotalStorageQuotaBytes()) {
            throw new IllegalArgumentException("当前用户总存储容量已接近上限，剩余额度 "
                    + humanSize(Math.max(0, current.userTotalStorageQuotaBytes() - currentStorageBytes)));
        }
    }

    public void recordUsage(String userId, long bytes) {
        String normalizedUser = normalizeUser(userId);
        increment(totalKey(), bytes);
        increment(userKey(normalizedUser), bytes);
    }

    private void validateQuota(String userId, long incomingBytes) {
        UploadPolicy current = currentPolicy();
        long todayTotal = usage(totalKey());
        long userTotal = usage(userKey(normalizeUser(userId)));
        if (todayTotal + incomingBytes > current.dailyTotalQuotaBytes()) {
            throw new IllegalArgumentException("今日系统总上传容量已接近上限，剩余额度 "
                    + humanSize(Math.max(0, current.dailyTotalQuotaBytes() - todayTotal)));
        }
        if (userTotal + incomingBytes > current.userDailyQuotaBytes()) {
            throw new IllegalArgumentException("当前用户今日上传容量已接近上限，剩余额度 "
                    + humanSize(Math.max(0, current.userDailyQuotaBytes() - userTotal)));
        }
    }

    private long usage(String key) {
        try {
            String value = stringRedisTemplate.opsForValue().get(key);
            return value == null ? 0L : Long.parseLong(value);
        } catch (RuntimeException ignored) {
            return localUsage.getOrDefault(key, 0L);
        }
    }

    private void increment(String key, long bytes) {
        try {
            stringRedisTemplate.opsForValue().increment(key, bytes);
            stringRedisTemplate.expire(key, Duration.ofDays(2));
        } catch (RuntimeException ignored) {
            localUsage.merge(key, bytes, Long::sum);
        }
    }

    private void persistPolicy(UploadPolicy next) {
        try {
            stringRedisTemplate.opsForValue().set(POLICY_KEY, objectMapper.writeValueAsString(next));
        } catch (JsonProcessingException | RuntimeException ignored) {
        }
    }

    private void loadPersistedPolicy() {
        try {
            String raw = stringRedisTemplate.opsForValue().get(POLICY_KEY);
            if (!StringUtils.hasText(raw)) {
                return;
            }
            UploadPolicy persisted = objectMapper.readValue(raw, UploadPolicy.class);
            UploadPolicy normalized = normalizePolicy(persisted);
            validatePolicy(normalized);
            policy.set(normalized);
        } catch (RuntimeException | JsonProcessingException ignored) {
        }
    }

    private String totalKey() {
        return "rag:upload:usage:total:" + LocalDate.now(ZoneId.systemDefault());
    }

    private String userKey(String userId) {
        return "rag:upload:usage:user:" + LocalDate.now(ZoneId.systemDefault()) + ":" + userId;
    }

    private String normalizeUser(String userId) {
        return StringUtils.hasText(userId) ? userId.trim() : "anonymous";
    }

    private void validatePolicy(UploadPolicy next) {
        if (next == null
                || next.directMaxFileBytes() <= 0
                || next.chunkSizeBytes() <= 0
                || next.maxChunkSizeBytes() <= 0
                || next.maxChunkedFileBytes() <= 0
                || next.dailyTotalQuotaBytes() <= 0
                || next.userDailyQuotaBytes() <= 0
                || next.userTotalStorageQuotaBytes() <= 0) {
            throw new IllegalArgumentException("上传策略必须全部为正数");
        }
        if (next.chunkSizeBytes() > next.maxChunkSizeBytes()) {
            throw new IllegalArgumentException("推荐分片大小不能大于最大分片大小");
        }
        if (next.directMaxFileBytes() > next.maxChunkedFileBytes()) {
            throw new IllegalArgumentException("普通上传上限不能大于分片上传文件上限");
        }
        if (next.allowedExtensions() == null || next.allowedExtensions().isEmpty()) {
            throw new IllegalArgumentException("文件类型白名单不能为空");
        }
    }

    private UploadPolicy normalizePolicy(UploadPolicy next) {
        if (next == null) {
            return null;
        }
        return new UploadPolicy(
                next.directMaxFileBytes(),
                next.chunkSizeBytes(),
                next.maxChunkSizeBytes(),
                next.maxChunkedFileBytes(),
                next.dailyTotalQuotaBytes(),
                next.userDailyQuotaBytes(),
                next.userTotalStorageQuotaBytes(),
                normalizeExtensions(next.allowedExtensions())
        );
    }

    private List<String> normalizeExtensions(List<String> values) {
        if (values == null) {
            return new ArrayList<>();
        }
        return values.stream()
                .filter(StringUtils::hasText)
                .map(value -> value.trim().replaceFirst("^\\.", "").toLowerCase())
                .distinct()
                .collect(Collectors.toList());
    }

    private String extensionOf(String fileName) {
        if (!StringUtils.hasText(fileName) || !fileName.contains(".")) {
            return "";
        }
        return fileName.substring(fileName.lastIndexOf('.') + 1).toLowerCase();
    }

    private String humanSize(long bytes) {
        if (bytes >= 1024L * 1024 * 1024) {
            return String.format("%.2fGB", bytes / 1024.0 / 1024.0 / 1024.0);
        }
        if (bytes >= 1024L * 1024) {
            return String.format("%.2fMB", bytes / 1024.0 / 1024.0);
        }
        if (bytes >= 1024L) {
            return String.format("%.2fKB", bytes / 1024.0);
        }
        return bytes + "B";
    }
}
