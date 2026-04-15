package com.team.rag.service;

import com.team.rag.bean.ChatMessage;

import java.util.List;

public interface ConversationMemoryService {

    void append(long memoryId, String role, String content);

    List<ChatMessage> getRecentMessages(long memoryId, int limit);

    List<ChatMessage> getAllMessages(long memoryId);

    List<Long> listMemoryIds(int limit);

    String getSummary(long memoryId);

    void replaceWithSummary(long memoryId, String summary, List<ChatMessage> retainedMessages);
}
