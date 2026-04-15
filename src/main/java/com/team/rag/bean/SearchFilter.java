package com.team.rag.bean;

import com.team.rag.entity.KnowledgeDocumentEntity;
import org.springframework.util.StringUtils;

import java.util.LinkedHashMap;
import java.util.Map;

public record SearchFilter(
        String sourceKind,
        String categoryPath,
        String businessScene,
        String fileType,
        String projectName,
        String department,
        String userId,
        String conversationId
) {

    public SearchFilter {
        sourceKind = normalizeSourceKind(sourceKind);
        if ("RAG".equalsIgnoreCase(sourceKind)) {
            userId = null;
            conversationId = null;
        }
    }

    public static SearchFilter empty() {
        return new SearchFilter(null, null, null, null, null, null, null, null);
    }

    public boolean active() {
        return StringUtils.hasText(sourceKind)
                || StringUtils.hasText(categoryPath)
                || StringUtils.hasText(businessScene)
                || StringUtils.hasText(fileType)
                || StringUtils.hasText(projectName)
                || StringUtils.hasText(department)
                || StringUtils.hasText(userId)
                || StringUtils.hasText(conversationId);
    }

    public boolean matches(KnowledgeDocumentEntity document) {
        String documentSourceKind = document.getSourceKind() == null || document.getSourceKind().isBlank() ? "RAG" : document.getSourceKind();
        boolean identityMatches = "RAG".equalsIgnoreCase(documentSourceKind)
                || (equalsIfPresent(userId, document.getUserId()) && equalsIfPresent(conversationId, document.getConversationId()));
        return equalsIfPresent(sourceKind, documentSourceKind)
                && categoryMatches(categoryPath, document.getCategoryPath())
                && equalsIfPresent(businessScene, document.getBusinessScene())
                && equalsIfPresent(fileType, document.getFileType())
                && equalsIfPresent(projectName, document.getProjectName())
                && equalsIfPresent(department, document.getDepartment())
                && identityMatches;
    }

    public Map<String, Object> toPineconeFilter() {
        Map<String, Object> filter = new LinkedHashMap<>();
        putEq(filter, "sourceKind", sourceKind);
        putEq(filter, "businessScene", businessScene);
        putEq(filter, "fileType", fileType);
        putEq(filter, "projectName", projectName);
        putEq(filter, "department", department);
        if (!"RAG".equalsIgnoreCase(sourceKind)) {
            putEq(filter, "userId", userId);
            putEq(filter, "conversationId", conversationId);
        }
        // categoryPath 支持父级分类前缀匹配，Pinecone 元数据过滤不做前缀过滤，
        // 因此这里交给本地二次过滤，避免父级分类漏掉子分类内容。
        return filter;
    }

    private static String normalizeSourceKind(String value) {
        if (!StringUtils.hasText(value) || "ALL".equalsIgnoreCase(value)) {
            return null;
        }
        return value.trim().toUpperCase();
    }

    private static boolean equalsIfPresent(String expected, String actual) {
        return !StringUtils.hasText(expected) || expected.equalsIgnoreCase(actual == null ? "" : actual);
    }

    private static boolean categoryMatches(String expected, String actual) {
        if (!StringUtils.hasText(expected)) {
            return true;
        }
        if (!StringUtils.hasText(actual)) {
            return false;
        }
        return actual.equalsIgnoreCase(expected) || actual.toLowerCase().startsWith(expected.toLowerCase() + "/");
    }

    private static void putEq(Map<String, Object> filter, String key, String value) {
        if (StringUtils.hasText(value)) {
            filter.put(key, Map.of("$eq", value));
        }
    }
}
