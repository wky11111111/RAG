package com.team.rag.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.unit.DataSize;

import java.util.ArrayList;
import java.util.List;

@ConfigurationProperties(prefix = "rag")
public class RagProperties {

    private Storage storage = new Storage();
    private Chunk chunk = new Chunk();
    private Retrieval retrieval = new Retrieval();
    private Answer answer = new Answer();
    private Upload upload = new Upload();
    private Admin admin = new Admin();

    public Storage getStorage() {
        return storage;
    }

    public void setStorage(Storage storage) {
        this.storage = storage;
    }

    public Chunk getChunk() {
        return chunk;
    }

    public void setChunk(Chunk chunk) {
        this.chunk = chunk;
    }

    public Retrieval getRetrieval() {
        return retrieval;
    }

    public void setRetrieval(Retrieval retrieval) {
        this.retrieval = retrieval;
    }

    public Answer getAnswer() {
        return answer;
    }

    public void setAnswer(Answer answer) {
        this.answer = answer;
    }

    public Upload getUpload() {
        return upload;
    }

    public void setUpload(Upload upload) {
        this.upload = upload;
    }

    public Admin getAdmin() {
        return admin;
    }

    public void setAdmin(Admin admin) {
        this.admin = admin;
    }

    public static class Storage {

        private String uploadDir;

        public String getUploadDir() {
            return uploadDir;
        }

        public void setUploadDir(String uploadDir) {
            this.uploadDir = uploadDir;
        }
    }

    public static class Chunk {

        private Integer maxLength;
        private Integer overlap;

        public Integer getMaxLength() {
            return maxLength;
        }

        public void setMaxLength(Integer maxLength) {
            this.maxLength = maxLength;
        }

        public Integer getOverlap() {
            return overlap;
        }

        public void setOverlap(Integer overlap) {
            this.overlap = overlap;
        }
    }

    public static class Retrieval {

        private Integer bm25TopK;
        private Integer vectorTopK;
        private Integer finalTopK;
        private Double minScore;

        public Integer getBm25TopK() {
            return bm25TopK;
        }

        public void setBm25TopK(Integer bm25TopK) {
            this.bm25TopK = bm25TopK;
        }

        public Integer getVectorTopK() {
            return vectorTopK;
        }

        public void setVectorTopK(Integer vectorTopK) {
            this.vectorTopK = vectorTopK;
        }

        public Integer getFinalTopK() {
            return finalTopK;
        }

        public void setFinalTopK(Integer finalTopK) {
            this.finalTopK = finalTopK;
        }

        public Double getMinScore() {
            return minScore;
        }

        public void setMinScore(Double minScore) {
            this.minScore = minScore;
        }
    }

    public static class Answer {

        private Integer maxCitations;
        private Integer historySize;
        private Integer cacheTtlMinutes;
        private Integer memorySummaryTokenThreshold = 32000;
        private Integer memorySummaryKeepMessages = 8;
        private Integer maxMemoryMessages = 160;

        public Integer getMaxCitations() {
            return maxCitations;
        }

        public void setMaxCitations(Integer maxCitations) {
            this.maxCitations = maxCitations;
        }

        public Integer getHistorySize() {
            return historySize;
        }

        public void setHistorySize(Integer historySize) {
            this.historySize = historySize;
        }

        public Integer getCacheTtlMinutes() {
            return cacheTtlMinutes;
        }

        public void setCacheTtlMinutes(Integer cacheTtlMinutes) {
            this.cacheTtlMinutes = cacheTtlMinutes;
        }

        public Integer getMemorySummaryTokenThreshold() {
            return memorySummaryTokenThreshold;
        }

        public void setMemorySummaryTokenThreshold(Integer memorySummaryTokenThreshold) {
            this.memorySummaryTokenThreshold = memorySummaryTokenThreshold;
        }

        public Integer getMemorySummaryKeepMessages() {
            return memorySummaryKeepMessages;
        }

        public void setMemorySummaryKeepMessages(Integer memorySummaryKeepMessages) {
            this.memorySummaryKeepMessages = memorySummaryKeepMessages;
        }

        public Integer getMaxMemoryMessages() {
            return maxMemoryMessages;
        }

        public void setMaxMemoryMessages(Integer maxMemoryMessages) {
            this.maxMemoryMessages = maxMemoryMessages;
        }
    }

    public static class Upload {

        private DataSize directMaxFileSize = DataSize.ofMegabytes(10);
        private DataSize chunkSize = DataSize.ofMegabytes(8);
        private DataSize maxChunkSize = DataSize.ofMegabytes(10);
        private DataSize maxChunkedFileSize = DataSize.ofGigabytes(2);
        private DataSize dailyTotalQuota = DataSize.ofGigabytes(5);
        private DataSize userDailyQuota = DataSize.ofGigabytes(1);
        private DataSize userTotalStorageQuota = DataSize.ofGigabytes(10);
        private List<String> allowedExtensions = new ArrayList<>(List.of(
                "txt", "md", "markdown", "csv", "json", "xml", "html", "htm", "log", "yml", "yaml", "pdf", "doc", "docx"
        ));

        public DataSize getDirectMaxFileSize() {
            return directMaxFileSize;
        }

        public void setDirectMaxFileSize(DataSize directMaxFileSize) {
            this.directMaxFileSize = directMaxFileSize;
        }

        public DataSize getChunkSize() {
            return chunkSize;
        }

        public void setChunkSize(DataSize chunkSize) {
            this.chunkSize = chunkSize;
        }

        public DataSize getMaxChunkSize() {
            return maxChunkSize;
        }

        public void setMaxChunkSize(DataSize maxChunkSize) {
            this.maxChunkSize = maxChunkSize;
        }

        public DataSize getMaxChunkedFileSize() {
            return maxChunkedFileSize;
        }

        public void setMaxChunkedFileSize(DataSize maxChunkedFileSize) {
            this.maxChunkedFileSize = maxChunkedFileSize;
        }

        public DataSize getDailyTotalQuota() {
            return dailyTotalQuota;
        }

        public void setDailyTotalQuota(DataSize dailyTotalQuota) {
            this.dailyTotalQuota = dailyTotalQuota;
        }

        public DataSize getUserDailyQuota() {
            return userDailyQuota;
        }

        public void setUserDailyQuota(DataSize userDailyQuota) {
            this.userDailyQuota = userDailyQuota;
        }

        public DataSize getUserTotalStorageQuota() {
            return userTotalStorageQuota;
        }

        public void setUserTotalStorageQuota(DataSize userTotalStorageQuota) {
            this.userTotalStorageQuota = userTotalStorageQuota;
        }

        public List<String> getAllowedExtensions() {
            return allowedExtensions;
        }

        public void setAllowedExtensions(List<String> allowedExtensions) {
            this.allowedExtensions = allowedExtensions;
        }
    }

    public static class Admin {

        private String token = "";

        public String getToken() {
            return token;
        }

        public void setToken(String token) {
            this.token = token;
        }
    }
}
