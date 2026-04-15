package com.team.rag.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.team.rag.bean.ChatMessage;
import com.team.rag.config.RagProperties;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;

@Service
public class RedisConversationMemoryService implements ConversationMemoryService {

    private static final Duration MEMORY_TTL = Duration.ofDays(3);
    private static final String SESSION_INDEX_KEY = "rag:memory:sessions";

    private final StringRedisTemplate stringRedisTemplate;
    private final ObjectMapper objectMapper;
    private final RagProperties ragProperties;
    private final Map<Long, Deque<ChatMessage>> localMessages = new ConcurrentHashMap<>();
    private final Map<Long, String> localSummaries = new ConcurrentHashMap<>();
    private final Map<Long, Long> localSessionUpdates = new ConcurrentHashMap<>();

    public RedisConversationMemoryService(StringRedisTemplate stringRedisTemplate,
                                          ObjectMapper objectMapper,
                                          RagProperties ragProperties) {
        this.stringRedisTemplate = stringRedisTemplate;
        this.objectMapper = objectMapper;
        this.ragProperties = ragProperties;
    }

    @Override
    public void append(long memoryId, String role, String content) {
        ChatMessage message = new ChatMessage(role, content);
        appendLocal(memoryId, message);

        String key = memoryKey(memoryId);
        try {
            stringRedisTemplate.opsForList().rightPush(key, objectMapper.writeValueAsString(message));
            stringRedisTemplate.opsForList().trim(key, -maxMemoryMessages(), -1);
            refreshRedisTtl(memoryId);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Conversation memory serialization failed", exception);
        } catch (RuntimeException ignored) {
        }
    }

    @Override
    public List<ChatMessage> getRecentMessages(long memoryId, int limit) {
        List<ChatMessage> messages = getAllMessages(memoryId);
        List<ChatMessage> recent;
        if (messages.size() <= limit) {
            recent = messages;
        } else {
            recent = messages.subList(messages.size() - limit, messages.size());
        }

        String summary = getSummary(memoryId);
        if (summary == null || summary.isBlank()) {
            return recent;
        }

        List<ChatMessage> withSummary = new ArrayList<>();
        withSummary.add(new ChatMessage("system", "历史会话摘要：" + summary));
        withSummary.addAll(recent);
        return withSummary;
    }

    @Override
    public List<ChatMessage> getAllMessages(long memoryId) {
        try {
            List<String> values = stringRedisTemplate.opsForList().range(memoryKey(memoryId), 0, -1);
            if (values == null || values.isEmpty()) {
                return localSnapshot(memoryId);
            }

            List<ChatMessage> messages = new ArrayList<>();
            for (String value : values) {
                try {
                    messages.add(objectMapper.readValue(value, ChatMessage.class));
                } catch (JsonProcessingException exception) {
                    throw new IllegalStateException("Conversation memory deserialization failed", exception);
                }
            }
            return messages;
        } catch (RuntimeException ignored) {
            return localSnapshot(memoryId);
        }
    }

    @Override
    public List<Long> listMemoryIds(int limit) {
        try {
            if (stringRedisTemplate.opsForZSet() != null) {
                var values = stringRedisTemplate.opsForZSet().reverseRange(SESSION_INDEX_KEY, 0, Math.max(0, limit - 1L));
                if (values != null && !values.isEmpty()) {
                    return values.stream()
                            .map(Long::valueOf)
                            .toList();
                }
            }
        } catch (RuntimeException ignored) {
        }

        return localSessionUpdates.entrySet().stream()
                .sorted(Map.Entry.<Long, Long>comparingByValue(Comparator.reverseOrder()))
                .limit(limit)
                .map(Map.Entry::getKey)
                .toList();
    }

    @Override
    public String getSummary(long memoryId) {
        try {
            String summary = stringRedisTemplate.opsForValue().get(summaryKey(memoryId));
            if (summary != null) {
                return summary;
            }
        } catch (RuntimeException ignored) {
        }
        return localSummaries.getOrDefault(memoryId, "");
    }

    @Override
    public void replaceWithSummary(long memoryId, String summary, List<ChatMessage> retainedMessages) {
        localSummaries.put(memoryId, summary);
        Deque<ChatMessage> deque = localMessages.computeIfAbsent(memoryId, ignored -> new ConcurrentLinkedDeque<>());
        deque.clear();
        retainedMessages.forEach(deque::addLast);
        localSessionUpdates.put(memoryId, System.currentTimeMillis());

        try {
            String key = memoryKey(memoryId);
            stringRedisTemplate.delete(key);
            for (ChatMessage message : retainedMessages) {
                stringRedisTemplate.opsForList().rightPush(key, objectMapper.writeValueAsString(message));
            }
            stringRedisTemplate.opsForValue().set(summaryKey(memoryId), summary, MEMORY_TTL);
            refreshRedisTtl(memoryId);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Conversation memory serialization failed", exception);
        } catch (RuntimeException ignored) {
        }
    }

    private void refreshRedisTtl(long memoryId) {
        String key = memoryKey(memoryId);
        stringRedisTemplate.expire(key, MEMORY_TTL);
        stringRedisTemplate.expire(summaryKey(memoryId), MEMORY_TTL);
        if (stringRedisTemplate.opsForZSet() != null) {
            stringRedisTemplate.opsForZSet().add(SESSION_INDEX_KEY, String.valueOf(memoryId), System.currentTimeMillis());
            stringRedisTemplate.expire(SESSION_INDEX_KEY, MEMORY_TTL);
        }
    }

    private String memoryKey(long memoryId) {
        return "rag:memory:" + memoryId;
    }

    private String summaryKey(long memoryId) {
        return "rag:memory:summary:" + memoryId;
    }

    private void appendLocal(long memoryId, ChatMessage message) {
        Deque<ChatMessage> deque = localMessages.computeIfAbsent(memoryId, ignored -> new ConcurrentLinkedDeque<>());
        deque.addLast(message);
        while (deque.size() > maxMemoryMessages()) {
            deque.pollFirst();
        }
        localSessionUpdates.put(memoryId, System.currentTimeMillis());
    }

    private int maxMemoryMessages() {
        Integer configured = ragProperties.getAnswer().getMaxMemoryMessages();
        return configured == null || configured < 16 ? 160 : configured;
    }

    private List<ChatMessage> localSnapshot(long memoryId) {
        Deque<ChatMessage> deque = localMessages.get(memoryId);
        if (deque == null || deque.isEmpty()) {
            return List.of();
        }
        return new ArrayList<>(deque);
    }
}
