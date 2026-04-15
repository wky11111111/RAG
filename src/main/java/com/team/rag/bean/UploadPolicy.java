package com.team.rag.bean;

public record UploadPolicy(
        long directMaxFileBytes,
        long chunkSizeBytes,
        long maxChunkSizeBytes,
        long maxChunkedFileBytes,
        long dailyTotalQuotaBytes,
        long userDailyQuotaBytes,
        long userTotalStorageQuotaBytes,
        java.util.List<String> allowedExtensions
) {
}
