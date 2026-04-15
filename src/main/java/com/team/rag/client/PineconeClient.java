package com.team.rag.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.team.rag.bean.PineconeMatch;
import com.team.rag.bean.SearchFilter;
import com.team.rag.bean.UpsertVector;
import com.team.rag.config.PineconeProperties;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
public class PineconeClient {

    private final RestClient.Builder restClientBuilder;
    private final PineconeProperties properties;
    private volatile String resolvedIndexHost;

    public PineconeClient(RestClient.Builder restClientBuilder, PineconeProperties properties) {
        this.restClientBuilder = restClientBuilder;
        this.properties = properties;
        this.resolvedIndexHost = properties.getIndexHost();
    }

    public void upsert(List<UpsertVector> vectors) {
        requireApiKey();
        ensureIndexReady();
        String namespace = namespaceFor(vectors);

        List<Map<String, Object>> payloadVectors = vectors.stream()
                .map(vector -> {
                    Map<String, Object> payload = new HashMap<>();
                    payload.put("id", vector.id());
                    payload.put("values", vector.values());
                    payload.put("metadata", vector.metadata());
                    return payload;
                })
                .toList();

        dataClient().post()
                .uri("/vectors/upsert")
                .body(Map.of(
                        "namespace", namespace,
                        "vectors", payloadVectors
                ))
                .retrieve()
                .toBodilessEntity();
    }

    public List<PineconeMatch> query(List<Float> vector, int topK) {
        return query(vector, topK, SearchFilter.empty());
    }

    public List<PineconeMatch> query(List<Float> vector, int topK, SearchFilter filter) {
        requireApiKey();
        ensureIndexReady();

        Map<String, Object> payload = new HashMap<>();
        payload.put("namespace", namespaceFor(filter));
        payload.put("topK", topK);
        payload.put("vector", vector);
        payload.put("includeMetadata", true);
        if (filter != null && filter.active()) {
            Map<String, Object> pineconeFilter = filter.toPineconeFilter();
            if (!pineconeFilter.isEmpty()) {
                payload.put("filter", pineconeFilter);
            }
        }

        JsonNode response = dataClient().post()
                .uri("/query")
                .body(payload)
                .retrieve()
                .body(JsonNode.class);

        List<PineconeMatch> matches = new ArrayList<>();
        for (JsonNode item : response.path("matches")) {
            Map<String, Object> metadata = new HashMap<>();
            item.path("metadata").fields().forEachRemaining(entry -> metadata.put(entry.getKey(), entry.getValue().asText()));
            matches.add(new PineconeMatch(
                    item.path("id").asText(),
                    item.path("score").asDouble(),
                    metadata
            ));
        }
        return matches;
    }

    public void deleteIds(List<String> ids) {
        deleteIds(ids, null);
    }

    public void deleteIds(List<String> ids, String sourceKind) {
        requireApiKey();
        ensureIndexReady();
        if (ids == null || ids.isEmpty()) {
            return;
        }

        dataClient().post()
                .uri("/vectors/delete")
                .body(Map.of(
                        "namespace", namespaceFor(sourceKind),
                        "ids", ids
                ))
                .retrieve()
                .toBodilessEntity();
    }

    private synchronized void ensureIndexReady() {
        if (StringUtils.hasText(resolvedIndexHost)) {
            return;
        }

        JsonNode description = describeIndex();
        if (description == null && Boolean.TRUE.equals(properties.getAutoCreateIndex())) {
            createIndex();
            description = waitUntilReady();
        }

        if (description == null) {
            throw new IllegalStateException("Pinecone 索引不存在，请配置 PINECONE_INDEX_HOST 或允许自动建索引");
        }

        resolvedIndexHost = description.path("host").asText();
        if (!StringUtils.hasText(resolvedIndexHost)) {
            throw new IllegalStateException("Pinecone 索引 host 解析失败");
        }
    }

    private JsonNode describeIndex() {
        try {
            return controlClient().get()
                    .uri("/indexes/{name}", properties.getIndexName())
                    .retrieve()
                    .body(JsonNode.class);
        } catch (HttpClientErrorException.NotFound exception) {
            return null;
        }
    }

    private void createIndex() {
        controlClient().post()
                .uri("/indexes")
                .body(Map.of(
                        "name", properties.getIndexName(),
                        "dimension", properties.getDimension(),
                        "metric", properties.getMetric(),
                        "spec", Map.of(
                                "serverless", Map.of(
                                        "cloud", properties.getCloud(),
                                        "region", properties.getRegion()
                                )
                        ),
                        "deletion_protection", "disabled"
                ))
                .retrieve()
                .toBodilessEntity();
    }

    private JsonNode waitUntilReady() {
        Instant deadline = Instant.now().plus(Duration.ofMinutes(2));
        while (Instant.now().isBefore(deadline)) {
            JsonNode description = describeIndex();
            if (description != null && description.path("status").path("ready").asBoolean(false)) {
                return description;
            }
            try {
                Thread.sleep(3000L);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("等待 Pinecone 索引就绪时被中断", exception);
            }
        }
        throw new IllegalStateException("Pinecone 索引创建后长时间未就绪");
    }

    private RestClient controlClient() {
        return restClientBuilder
                .baseUrl(properties.getControlUrl())
                .defaultHeader("Api-Key", properties.getApiKey())
                .defaultHeader("X-Pinecone-API-Version", properties.getApiVersion())
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .build();
    }

    private RestClient dataClient() {
        return restClientBuilder
                .baseUrl(normalizeHost(resolvedIndexHost))
                .defaultHeader("Api-Key", properties.getApiKey())
                .defaultHeader("X-Pinecone-API-Version", properties.getApiVersion())
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .build();
    }

    private String namespaceFor(List<UpsertVector> vectors) {
        if (vectors == null || vectors.isEmpty()) {
            return namespaceFor((String) null);
        }
        Object sourceKind = vectors.get(0).metadata().get("sourceKind");
        return namespaceFor(sourceKind == null ? null : sourceKind.toString());
    }

    private String namespaceFor(SearchFilter filter) {
        return namespaceFor(filter == null ? null : filter.sourceKind());
    }

    private String namespaceFor(String sourceKind) {
        String base = StringUtils.hasText(properties.getNamespace()) ? properties.getNamespace() : "team-rag-knowledge";
        if (!StringUtils.hasText(sourceKind) || "ALL".equalsIgnoreCase(sourceKind)) {
            return base;
        }
        return base + "-" + sourceKind.trim().toLowerCase();
    }

    private void requireApiKey() {
        if (!StringUtils.hasText(properties.getApiKey())) {
            throw new IllegalStateException("Pinecone API Key 未配置，请在本地配置 PINECONE_API_KEY");
        }
    }

    private String normalizeHost(String host) {
        if (host.startsWith("http://") || host.startsWith("https://")) {
            return host;
        }
        return "https://" + host;
    }
}
