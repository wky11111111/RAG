package com.team.rag.service;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.concurrent.atomic.AtomicReference;

@Service
public class SystemPromptService {

    private static final String PROMPT_KEY = "rag:system-prompt";
    private static final String DEFAULT_PROMPT = """
            你是一个企业知识库助手。
            请严格基于提供的知识库上下文回答。
            如果上下文不足，请明确说明“知识库中未找到足够依据”。
            回答要简洁、专业，并尽量给出条目化总结。
            """;

    private final StringRedisTemplate stringRedisTemplate;
    private final AtomicReference<String> localPrompt = new AtomicReference<>(DEFAULT_PROMPT);

    public SystemPromptService(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }

    public String getPrompt() {
        try {
            String prompt = stringRedisTemplate.opsForValue().get(PROMPT_KEY);
            if (StringUtils.hasText(prompt)) {
                localPrompt.set(prompt);
                return prompt;
            }
        } catch (RuntimeException ignored) {
        }
        return localPrompt.get();
    }

    public String updatePrompt(String prompt) {
        String effective = StringUtils.hasText(prompt) ? prompt.trim() : DEFAULT_PROMPT;
        if (effective.length() > 8000) {
            throw new IllegalArgumentException("系统提示词不能超过 8000 字符");
        }
        localPrompt.set(effective);
        try {
            stringRedisTemplate.opsForValue().set(PROMPT_KEY, effective);
        } catch (RuntimeException ignored) {
        }
        return effective;
    }
}
