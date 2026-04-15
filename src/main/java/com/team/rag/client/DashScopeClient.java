package com.team.rag.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.team.rag.bean.ChatMessage;
import com.team.rag.config.DashScopeProperties;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

@Component
public class DashScopeClient {

    private static final int MAX_EMBEDDING_BATCH_SIZE = 10;

    private final RestClient restClient;
    private final DashScopeProperties properties;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient = HttpClient.newHttpClient();

    public DashScopeClient(RestClient.Builder builder, DashScopeProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.restClient = builder
                .baseUrl(properties.getBaseUrl())
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + properties.getApiKey())
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .build();
    }

    public List<List<Float>> embedAll(List<String> texts) {
        requireApiKey();
        if (texts == null || texts.isEmpty()) {
            return List.of();
        }

        List<List<Float>> vectors = new ArrayList<>();
        for (int start = 0; start < texts.size(); start += MAX_EMBEDDING_BATCH_SIZE) {
            int end = Math.min(start + MAX_EMBEDDING_BATCH_SIZE, texts.size());
            vectors.addAll(embedBatch(texts.subList(start, end)));
        }
        return vectors;
    }

    private List<List<Float>> embedBatch(List<String> texts) {
        JsonNode response = restClient.post()
                .uri("/embeddings")
                .body(Map.of(
                        "model", properties.getEmbeddingModel(),
                        "input", texts,
                        "dimensions", properties.getEmbeddingDimensions(),
                        "encoding_format", "float"
                ))
                .retrieve()
                .body(JsonNode.class);

        JsonNode data = response.path("data");
        List<List<Float>> vectors = new ArrayList<>();
        for (JsonNode item : data) {
            List<Float> vector = new ArrayList<>();
            for (JsonNode value : item.path("embedding")) {
                vector.add(value.floatValue());
            }
            vectors.add(vector);
        }
        return vectors;
    }

    public String chat(List<ChatMessage> messages) {
        requireApiKey();
        JsonNode response = restClient.post()
                .uri("/chat/completions")
                .body(Map.of(
                        "model", properties.getChatModel(),
                        "temperature", properties.getTemperature(),
                        "messages", toPayloadMessages(messages)
                ))
                .retrieve()
                .body(JsonNode.class);

        return contentText(response.path("choices").path(0).path("message").path("content"));
    }

    public String streamChat(List<ChatMessage> messages, Consumer<String> tokenConsumer) {
        requireApiKey();
        try {
            String requestBody = objectMapper.writeValueAsString(Map.of(
                    "model", properties.getChatModel(),
                    "temperature", properties.getTemperature(),
                    "stream", true,
                    "messages", toPayloadMessages(messages)
            ));

            HttpRequest request = HttpRequest.newBuilder(chatCompletionsUri())
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + properties.getApiKey())
                    .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                    .header(HttpHeaders.ACCEPT, MediaType.TEXT_EVENT_STREAM_VALUE)
                    .POST(HttpRequest.BodyPublishers.ofString(requestBody, StandardCharsets.UTF_8))
                    .build();

            HttpResponse<InputStream> response = httpClient.send(request, HttpResponse.BodyHandlers.ofInputStream());
            if (response.statusCode() >= 400) {
                String errorBody = new String(response.body().readAllBytes(), StandardCharsets.UTF_8);
                throw new IllegalStateException("DashScope stream request failed: " + errorBody);
            }

            StringBuilder fullAnswer = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(response.body(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    String data = parseSseData(line);
                    if (data == null) {
                        continue;
                    }
                    if ("[DONE]".equals(data)) {
                        break;
                    }

                    String delta = extractStreamDelta(data);
                    if (!delta.isBlank()) {
                        fullAnswer.append(delta);
                        tokenConsumer.accept(delta);
                    }
                }
            }
            return fullAnswer.toString();
        } catch (Exception exception) {
            throw new IllegalStateException("DashScope stream chat failed: " + exception.getMessage(), exception);
        }
    }

    private List<Map<String, String>> toPayloadMessages(List<ChatMessage> messages) {
        return messages.stream()
                .map(message -> {
                    Map<String, String> payload = new LinkedHashMap<>();
                    payload.put("role", message.role());
                    payload.put("content", message.content());
                    return payload;
                })
                .toList();
    }

    private URI chatCompletionsUri() {
        String baseUrl = properties.getBaseUrl();
        while (baseUrl.endsWith("/")) {
            baseUrl = baseUrl.substring(0, baseUrl.length() - 1);
        }
        return URI.create(baseUrl + "/chat/completions");
    }

    private String parseSseData(String line) {
        if (line == null) {
            return null;
        }
        String trimmed = line.trim();
        if (!trimmed.startsWith("data:")) {
            return null;
        }
        return trimmed.substring("data:".length()).trim();
    }

    private String extractStreamDelta(String data) throws java.io.IOException {
        JsonNode root = objectMapper.readTree(data);
        JsonNode choice = root.path("choices").path(0);
        JsonNode contentNode = choice.path("delta").path("content");
        if (contentNode.isMissingNode() || contentNode.isNull()) {
            contentNode = choice.path("message").path("content");
        }
        return contentText(contentNode);
    }

    private String contentText(JsonNode contentNode) {
        if (contentNode == null || contentNode.isMissingNode() || contentNode.isNull()) {
            return "";
        }
        if (contentNode.isArray()) {
            StringBuilder builder = new StringBuilder();
            for (JsonNode item : contentNode) {
                builder.append(item.path("text").asText(""));
            }
            return builder.toString();
        }
        return contentNode.asText("");
    }

    private void requireApiKey() {
        if (!StringUtils.hasText(properties.getApiKey())) {
            throw new IllegalStateException("DashScope API Key 未配置，请在本地配置 DASHSCOPE_API_KEY");
        }
    }
}
