package com.team.rag.service;

import com.team.rag.bean.ChatMessage;
import com.team.rag.client.DashScopeClient;
import com.team.rag.client.DeepSeekClient;
import com.team.rag.config.ChatModelProperties;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;

@Service
public class ChatModelService {

    public static final String PROVIDER_DASHSCOPE = "dashscope";
    public static final String PROVIDER_DEEPSEEK = "deepseek";

    private final DashScopeClient dashScopeClient;
    private final DeepSeekClient deepSeekClient;
    private final ChatModelProperties properties;

    public ChatModelService(DashScopeClient dashScopeClient,
                            DeepSeekClient deepSeekClient,
                            ChatModelProperties properties) {
        this.dashScopeClient = dashScopeClient;
        this.deepSeekClient = deepSeekClient;
        this.properties = properties;
    }

    public String resolveProvider(String requestedProvider) {
        String provider = StringUtils.hasText(requestedProvider)
                ? requestedProvider
                : properties.getDefaultProvider();
        provider = StringUtils.hasText(provider) ? provider.trim().toLowerCase(Locale.ROOT) : PROVIDER_DASHSCOPE;
        if (!PROVIDER_DEEPSEEK.equals(provider)) {
            return PROVIDER_DASHSCOPE;
        }
        return PROVIDER_DEEPSEEK;
    }

    public String chat(String provider, List<ChatMessage> messages) {
        return switch (resolveProvider(provider)) {
            case PROVIDER_DEEPSEEK -> deepSeekClient.chat(messages);
            default -> dashScopeClient.chat(messages);
        };
    }

    public String streamChat(String provider, List<ChatMessage> messages, Consumer<String> tokenConsumer) {
        return switch (resolveProvider(provider)) {
            case PROVIDER_DEEPSEEK -> deepSeekClient.streamChat(messages, tokenConsumer);
            default -> dashScopeClient.streamChat(messages, tokenConsumer);
        };
    }
}
