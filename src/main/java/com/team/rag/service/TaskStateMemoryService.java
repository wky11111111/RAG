package com.team.rag.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.team.rag.bean.TaskStateEntry;
import com.team.rag.util.TextTokenizer;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

@Service
public class TaskStateMemoryService {

    private static final long STATE_TTL_DAYS = 7;
    private final StringRedisTemplate stringRedisTemplate;
    private final ObjectMapper objectMapper;
    private final TextTokenizer textTokenizer;

    public TaskStateMemoryService(StringRedisTemplate stringRedisTemplate,
                                  ObjectMapper objectMapper,
                                  TextTokenizer textTokenizer) {
        this.stringRedisTemplate = stringRedisTemplate;
        this.objectMapper = objectMapper;
        this.textTokenizer = textTokenizer;
    }

    /**
     * 分层存储、版本覆盖写入方法
     * 按任务（taskId）、状态分类（stateCategory）、状态键（stateKey）结构化存储状态。
     * 同一个键会被新版本覆盖，减少冗余与混淆。
     */
    public void upsertState(String taskId, String stateCategory, String stateKey, String content) {
        String redisKey = buildRedisKey(taskId, stateCategory);
        TaskStateEntry entry = new TaskStateEntry(taskId, stateCategory, stateKey, content, System.currentTimeMillis());
        try {
            String value = objectMapper.writeValueAsString(entry);
            stringRedisTemplate.opsForHash().put(redisKey, stateKey, value);
            stringRedisTemplate.expire(redisKey, STATE_TTL_DAYS, TimeUnit.DAYS);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Failed to serialize TaskStateEntry", exception);
        }
    }

    /**
     * 获取指定任务和状态类别下的所有状态信息
     */
    public List<TaskStateEntry> getAllStates(String taskId, String stateCategory) {
        String redisKey = buildRedisKey(taskId, stateCategory);
        Map<Object, Object> entries = stringRedisTemplate.opsForHash().entries(redisKey);
        if (entries.isEmpty()) {
            return List.of();
        }

        List<TaskStateEntry> states = new ArrayList<>();
        for (Object value : entries.values()) {
            try {
                states.add(objectMapper.readValue((String) value, TaskStateEntry.class));
            } catch (JsonProcessingException ignored) {
            }
        }
        return states;
    }

    /**
     * 按任务和状态过滤并基于相似度（BM25）排序的检索方法
     */
    public List<TaskStateEntry> searchState(String taskId, String stateCategory, String query, int topK) {
        List<TaskStateEntry> states = getAllStates(taskId, stateCategory);
        if (states.isEmpty()) {
            return List.of();
        }

        List<String> queryTokens = textTokenizer.tokenize(query);
        if (queryTokens.isEmpty()) {
            return states.stream()
                    .sorted(Comparator.comparingLong(TaskStateEntry::getTimestamp).reversed())
                    .limit(topK)
                    .toList();
        }

        Map<String, Long> documentFrequency = new HashMap<>();
        Map<String, List<String>> chunkTokens = new HashMap<>();
        double totalLength = 0.0;

        for (TaskStateEntry state : states) {
            // 将键名和内容合并进行分词，提升检索匹配度
            List<String> tokens = textTokenizer.tokenize(state.getStateKey() + " " + state.getContent());
            chunkTokens.put(state.getStateKey(), tokens);
            totalLength += tokens.size();

            Set<String> seen = new HashSet<>(tokens);
            for (String token : seen) {
                documentFrequency.merge(token, 1L, Long::sum);
            }
        }

        double avgDocLength = Math.max(1.0, totalLength / states.size());
        Set<String> uniqueQueryTerms = new HashSet<>(queryTokens);

        record ScoredState(TaskStateEntry state, double score) {}
        List<ScoredState> hits = new ArrayList<>();

        for (TaskStateEntry state : states) {
            List<String> tokens = chunkTokens.get(state.getStateKey());
            if (tokens == null || tokens.isEmpty()) {
                continue;
            }

            Map<String, Integer> tf = textTokenizer.countTokens(tokens);
            double score = 0.0;
            for (String term : uniqueQueryTerms) {
                int frequency = tf.getOrDefault(term, 0);
                if (frequency == 0) {
                    continue;
                }

                long df = documentFrequency.getOrDefault(term, 0L);
                double idf = Math.log(1.0 + (states.size() - df + 0.5) / (df + 0.5));
                double k1 = 1.5;
                double b = 0.75;
                double denominator = frequency + k1 * (1 - b + b * tokens.size() / avgDocLength);
                score += idf * (frequency * (k1 + 1)) / denominator;
            }
            if (score > 0.0) {
                hits.add(new ScoredState(state, score));
            }
        }

        return hits.stream()
                .sorted(Comparator.comparingDouble(ScoredState::score).reversed())
                .limit(topK)
                .map(ScoredState::state)
                .toList();
    }

    /**
     * 删除特定的任务状态键
     */
    public void deleteState(String taskId, String stateCategory, String stateKey) {
        String redisKey = buildRedisKey(taskId, stateCategory);
        stringRedisTemplate.opsForHash().delete(redisKey, stateKey);
    }

    private String buildRedisKey(String taskId, String stateCategory) {
        return "rag:task_state:" + taskId + ":" + stateCategory;
    }
}
